package com.template.connect;

import com.template.todos.Todo;
import com.template.todos.TodoRepository;
import com.template.todos.TodoService;
import com.template.todos.v1.CreateTodoRequest;
import com.template.todos.v1.GetTodosRequest;
import com.template.todos.v1.GetTodosResponse;
import com.template.todos.v1.TodosServiceGrpc;
import dev.neilmason.boot.connect.test.AutoConfigureConnectTestClient;
import dev.neilmason.boot.connect.test.ConnectTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "spring.flyway.enabled=false"
)
@EnableAutoConfiguration(exclude = {
    R2dbcAutoConfiguration.class,
    DataSourceAutoConfiguration.class
})
@AutoConfigureConnectTestClient
class ConnectEndpointTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");
    private static final String TEST_USER = "test-user";

    // springSecurity() registers Spring Security's WebTestClient mock support (needed for
    // mockJwt() to have any effect), but it returns a MockServerConfigurer -- a type only
    // WebTestClient.MockServerSpec.apply() accepts (i.e. only the
    // .bindToApplicationContext(ctx).apply(springSecurity()) construction path).
    // WebTestClient.Builder.apply() and ConnectTestClient.mutateWith() both require a
    // WebTestClientConfigurer instead, which is a different, incompatible interface -- so
    // springSecurity() cannot be wired in via the auto-configured WebTestClient.Builder at
    // all. Defining our own WebTestClient bean, built the old way, lets
    // ConnectTestClientAutoConfiguration back off its own and consume this one instead.
    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestClientConfig {
        @Bean
        WebTestClient webTestClient(ApplicationContext applicationContext) {
            return WebTestClient.bindToApplicationContext(applicationContext)
                .apply(SecurityMockServerConfigurers.springSecurity())
                .build();
        }
    }

    @Autowired
    private ConnectTestClient connectTestClient;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TodoService todoService;

    @MockitoBean
    private TodoRepository todoRepository;

    private ConnectTestClient authenticated() {
        return connectTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(TEST_USER)));
    }

    @Test
    void getTodos_shouldReturnProtobufResponse() {
        UUID todoId = UUID.randomUUID();
        Instant now = Instant.now();
        Todo todo = new Todo(todoId, TEST_USER, "Test Todo", null, now);

        when(todoService.getTodos(TEST_USER)).thenReturn(Flux.just(todo));

        GetTodosResponse response = authenticated().call(
            TodosServiceGrpc.getGetTodosMethod(),
            GetTodosRequest.getDefaultInstance());

        assertThat(response.getTodosCount()).isEqualTo(1);
        assertThat(response.getTodos(0).getId()).isEqualTo(todoId.toString());
        assertThat(response.getTodos(0).getTitle()).isEqualTo("Test Todo");
    }

    @Test
    void createTodo_shouldReturnCreatedTodo() {
        UUID todoId = UUID.randomUUID();
        Instant now = Instant.now();
        Todo todo = new Todo(todoId, TEST_USER, "New Todo", null, now);

        when(todoService.createTodo(eq(TEST_USER), eq("New Todo"))).thenReturn(Mono.just(todo));

        com.template.todos.v1.Todo protoTodo = authenticated().call(
            TodosServiceGrpc.getCreateTodoMethod(),
            CreateTodoRequest.newBuilder().setTitle("New Todo").build());

        assertThat(protoTodo.getId()).isEqualTo(todoId.toString());
        assertThat(protoTodo.getTitle()).isEqualTo("New Todo");
    }

    // Error-path tests stay on raw WebTestClient: ConnectTestClient.call() asserts
    // isOk() internally, so it cannot observe a 4xx Connect error response.

    @Test
    void unknownMethod_shouldReturn404() {
        webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(TEST_USER)))
            .post()
            .uri("/connect/todos.v1.TodosService/NonExistent")
            .contentType(APPLICATION_PROTO)
            .bodyValue(new byte[0])
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.code").isEqualTo("unimplemented");
    }

    @Test
    void unauthenticated_shouldReturn401() {
        webTestClient
            .post()
            .uri("/connect/todos.v1.TodosService/GetTodos")
            .contentType(APPLICATION_PROTO)
            .bodyValue(new byte[0])
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
