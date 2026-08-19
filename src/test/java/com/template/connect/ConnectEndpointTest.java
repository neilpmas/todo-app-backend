package com.template.connect;

import com.template.todos.Todo;
import com.template.todos.TodoRepository;
import com.template.todos.TodoService;
import com.template.todos.v1.CreateTodoRequest;
import com.template.todos.v1.GetTodosRequest;
import com.template.todos.v1.GetTodosResponse;
import com.template.todos.v1.TodosServiceGrpc;
import dev.neilmason.boot.connect.test.AutoConfigureConnectTestClient;
import dev.neilmason.boot.connect.test.ConnectError;
import dev.neilmason.boot.connect.test.ConnectTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.MockServerConfigurer;
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

    private static final String TEST_USER = "test-user";
    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfig {
        @Bean
        MockServerConfigurer springSecurityConfigurer() {
            return SecurityMockServerConfigurers.springSecurity();
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

    @Test
    void unknownMethod_shouldReturn404() {
        ConnectError error = authenticated().callExpectingError(
            TodosServiceGrpc.getGetTodosMethod().toBuilder()
                .setFullMethodName("todos.v1.TodosService/NonExistent")
                .build(),
            GetTodosRequest.getDefaultInstance());

        assertThat(error.code()).isEqualTo("unimplemented");
    }

    // Stays on raw WebTestClient: an unauthenticated request never reaches the Connect
    // protocol layer at all -- Spring Security's default BearerTokenAuthenticationEntryPoint
    // rejects it at the filter chain with a bare 401 and no body, so there's no Connect JSON
    // error for callExpectingError to parse (it requires a response body -- see
    // connectrpc-spring-boot#9/#10 fix notes; this is a new, separate gap, worth its own
    // upstream issue).
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
