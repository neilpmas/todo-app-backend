package com.template.connect;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ConnectFilter implements WebFilter {

    private static final String CONNECT_PREFIX = "/connect/";
    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    private static final Map<Status.Code, ConnectError> STATUS_MAP = Map.ofEntries(
        Map.entry(Status.Code.INVALID_ARGUMENT, new ConnectError("invalid_argument", HttpStatus.BAD_REQUEST)),
        Map.entry(Status.Code.NOT_FOUND, new ConnectError("not_found", HttpStatus.NOT_FOUND)),
        Map.entry(Status.Code.ALREADY_EXISTS, new ConnectError("already_exists", HttpStatus.CONFLICT)),
        Map.entry(Status.Code.PERMISSION_DENIED, new ConnectError("permission_denied", HttpStatus.FORBIDDEN)),
        Map.entry(Status.Code.UNAUTHENTICATED, new ConnectError("unauthenticated", HttpStatus.UNAUTHORIZED)),
        Map.entry(Status.Code.UNAVAILABLE, new ConnectError("unavailable", HttpStatus.SERVICE_UNAVAILABLE)),
        Map.entry(Status.Code.UNIMPLEMENTED, new ConnectError("unimplemented", HttpStatus.NOT_FOUND)),
        Map.entry(Status.Code.INTERNAL, new ConnectError("internal", HttpStatus.INTERNAL_SERVER_ERROR)),
        Map.entry(Status.Code.UNKNOWN, new ConnectError("unknown", HttpStatus.INTERNAL_SERVER_ERROR))
    );

    private final ConnectServiceRegistry registry;

    public ConnectFilter(ConnectServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (!HttpMethod.POST.equals(request.getMethod()) || !path.startsWith(CONNECT_PREFIX)) {
            return chain.filter(exchange);
        }

        String remaining = path.substring(CONNECT_PREFIX.length());
        int lastSlash = remaining.lastIndexOf('/');
        if (lastSlash <= 0) {
            return writeConnectError(exchange.getResponse(), "unimplemented", "Invalid path", HttpStatus.NOT_FOUND);
        }

        String serviceName = remaining.substring(0, lastSlash);
        String methodName = remaining.substring(lastSlash + 1);

        ConnectServiceRegistry.MethodEntry entry = registry.lookup(serviceName, methodName);
        if (entry == null) {
            return writeConnectError(exchange.getResponse(), "unimplemented",
                "Method not found: " + serviceName + "/" + methodName, HttpStatus.NOT_FOUND);
        }

        return DataBufferUtils.join(request.getBody())
            .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
            .flatMap(dataBuffer -> {
                byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bodyBytes);
                DataBufferUtils.release(dataBuffer);
                return invokeMethod(exchange, entry, bodyBytes);
            });
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> invokeMethod(
            ServerWebExchange exchange,
            ConnectServiceRegistry.MethodEntry entry,
            byte[] bodyBytes) {

        MethodDescriptor<Object, Object> descriptor =
            (MethodDescriptor<Object, Object>) entry.descriptor();

        Object requestMessage;
        try {
            requestMessage = descriptor.parseRequest(new ByteArrayInputStream(bodyBytes));
        } catch (Exception e) {
            return writeConnectError(exchange.getResponse(), "invalid_argument",
                "Failed to parse request: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        return ReactiveSecurityContextHolder.getContext()
            .defaultIfEmpty(new SecurityContextImpl())
            .flatMap(securityContext -> {
                CompletableFuture<Object> future = new CompletableFuture<>();

                StreamObserver<Object> responseObserver = new StreamObserver<>() {
                    private Object value;

                    @Override
                    public void onNext(Object resp) {
                        this.value = resp;
                    }

                    @Override
                    public void onError(Throwable t) {
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        future.complete(value);
                    }
                };

                return Mono.fromCallable(() -> {
                        SecurityContextHolder.setContext(securityContext);
                        try {
                            entry.javaMethod().invoke(entry.service(), requestMessage, responseObserver);
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            future.completeExceptionally(cause);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                        return future;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(Mono::fromFuture)
                    .flatMap(response -> writeProtoResponse(exchange.getResponse(), descriptor, response))
                    .onErrorResume(throwable -> handleError(exchange.getResponse(), throwable));
            });
    }

    private Mono<Void> writeProtoResponse(
            ServerHttpResponse response,
            MethodDescriptor<Object, Object> descriptor,
            Object message) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(APPLICATION_PROTO);
        try (var stream = descriptor.streamResponse(message)) {
            byte[] bytes = stream.readAllBytes();
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return writeConnectError(response, "internal",
                "Failed to serialize response", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Void> handleError(ServerHttpResponse response, Throwable throwable) {
        if (throwable instanceof StatusRuntimeException sre) {
            Status.Code code = sre.getStatus().getCode();
            ConnectError error = STATUS_MAP.getOrDefault(code,
                new ConnectError("unknown", HttpStatus.INTERNAL_SERVER_ERROR));
            String message = sre.getStatus().getDescription();
            if (message == null) {
                message = sre.getMessage();
            }
            return writeConnectError(response, error.code(), message, error.httpStatus());
        }
        return writeConnectError(response, "internal",
            throwable.getMessage() != null ? throwable.getMessage() : "Internal error",
            HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Mono<Void> writeConnectError(
            ServerHttpResponse response, String code, String message, HttpStatus status) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"code\":\"" + escapeJson(code)
            + "\",\"message\":\"" + escapeJson(message) + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ConnectError(String code, HttpStatus httpStatus) {}
}
