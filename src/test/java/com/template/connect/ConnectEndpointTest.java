package com.template.connect;

import com.template.todos.Todo;
import com.template.todos.TodoRepository;
import com.template.todos.TodoService;
import com.template.todos.v1.CreateTodoRequest;
import com.template.todos.v1.GetTodosRequest;
import com.template.todos.v1.GetTodosResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
class ConnectEndpointTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private TodoService todoService;

    @MockitoBean
    private TodoRepository todoRepository;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .build();
    }

    @Test
    void getTodos_shouldReturnProtobufResponse() throws Exception {
        UUID todoId = UUID.randomUUID();
        Instant now = Instant.now();
        Todo todo = new Todo(todoId, "test-user", "Test Todo", null, now);

        when(todoService.getTodos("test-user")).thenReturn(Flux.just(todo));

        byte[] responseBytes = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject("test-user")))
            .post()
            .uri("/connect/todos.v1.TodosService/GetTodos")
            .contentType(APPLICATION_PROTO)
            .bodyValue(GetTodosRequest.getDefaultInstance().toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_PROTO)
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBytes).isNotNull();
        GetTodosResponse response = GetTodosResponse.parseFrom(responseBytes);
        assertThat(response.getTodosCount()).isEqualTo(1);
        assertThat(response.getTodos(0).getId()).isEqualTo(todoId.toString());
        assertThat(response.getTodos(0).getTitle()).isEqualTo("Test Todo");
    }

    @Test
    void createTodo_shouldReturnCreatedTodo() throws Exception {
        UUID todoId = UUID.randomUUID();
        Instant now = Instant.now();
        Todo todo = new Todo(todoId, "test-user", "New Todo", null, now);

        when(todoService.createTodo(eq("test-user"), eq("New Todo"))).thenReturn(Mono.just(todo));

        CreateTodoRequest request = CreateTodoRequest.newBuilder()
            .setTitle("New Todo")
            .build();

        byte[] responseBytes = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject("test-user")))
            .post()
            .uri("/connect/todos.v1.TodosService/CreateTodo")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(APPLICATION_PROTO)
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBytes).isNotNull();
        com.template.todos.v1.Todo protoTodo = com.template.todos.v1.Todo.parseFrom(responseBytes);
        assertThat(protoTodo.getId()).isEqualTo(todoId.toString());
        assertThat(protoTodo.getTitle()).isEqualTo("New Todo");
    }

    @Test
    void unknownMethod_shouldReturn404() {
        webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject("test-user")))
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
