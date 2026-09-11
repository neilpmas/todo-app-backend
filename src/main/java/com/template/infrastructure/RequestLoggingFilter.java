package com.template.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Logs one line per request (method, path, status, duration, request id) and threads a
 * correlation id through to the response so it can be matched against the BFF's own logs.
 *
 * <p>Runs before Spring Security so unauthenticated 401s are logged too -- the previous
 * state of this codebase logged nothing at all for the request path, auth failures
 * included, which is exactly the gap this closes.
 *
 * <p>Deliberately does not attempt to thread the request id into deeper business-logic
 * logging (e.g. {@code TodoService}) via MDC or Reactor context: {@code ConnectFilter}
 * dispatches to blocking-style {@code @GrpcService} methods (see the note on
 * {@link MigrationGateWebFilter}), and MDC does not reliably survive that scheduler
 * hop. Business-logic logs are correlated by user id and timestamp instead -- see
 * {@code TodoService} -- which is reliable regardless of which thread runs what.
 */
@Component
public class RequestLoggingFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = firstHeaderOrNew(request.getHeaders());
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

        String method = request.getMethod().name();
        String path = request.getPath().value();
        long startNanos = System.nanoTime();

        // Runs regardless of how the chain finished (success, error, or client
        // cancellation) -- every request gets exactly one log line either way.
        return chain.filter(exchange)
            .doFinally(ignoredSignalType -> {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
                log.atInfo()
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("method", method)
                    .addKeyValue("path", path)
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", durationMs)
                    .log("request completed");
            });
    }

    private String firstHeaderOrNew(HttpHeaders headers) {
        String incoming = headers.getFirst(REQUEST_ID_HEADER);
        return (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
    }
}
