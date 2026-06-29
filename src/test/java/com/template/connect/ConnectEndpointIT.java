package com.template.connect;

import com.template.AbstractIntegrationTest;
import com.template.todos.v1.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ConnectEndpointIT extends AbstractIntegrationTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");
    private static final String USER_ID = "integration-test-user";

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .build();
    }

    @Test
    void fullTodoLifecycle() throws Exception {
        // Create a todo
        CreateTodoRequest createRequest = CreateTodoRequest.newBuilder()
            .setTitle("Integration Test Todo")
            .build();

        byte[] createResponse = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(USER_ID)))
            .post()
            .uri("/connect/todos.v1.TodosService/CreateTodo")
            .contentType(APPLICATION_PROTO)
            .bodyValue(createRequest.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        Todo createdTodo = Todo.parseFrom(createResponse);
        assertThat(createdTodo.getTitle()).isEqualTo("Integration Test Todo");
        assertThat(createdTodo.getUserId()).isEqualTo(USER_ID);
        String todoId = createdTodo.getId();

        // List todos
        byte[] listResponse = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(USER_ID)))
            .post()
            .uri("/connect/todos.v1.TodosService/GetTodos")
            .contentType(APPLICATION_PROTO)
            .bodyValue(GetTodosRequest.getDefaultInstance().toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        GetTodosResponse todosResponse = GetTodosResponse.parseFrom(listResponse);
        assertThat(todosResponse.getTodosList())
            .anyMatch(t -> t.getId().equals(todoId));

        // Complete the todo
        CompleteTodoRequest completeRequest = CompleteTodoRequest.newBuilder()
            .setId(todoId)
            .build();

        byte[] completeResponse = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(USER_ID)))
            .post()
            .uri("/connect/todos.v1.TodosService/CompleteTodo")
            .contentType(APPLICATION_PROTO)
            .bodyValue(completeRequest.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        Todo completedTodo = Todo.parseFrom(completeResponse);
        assertThat(completedTodo.hasCompletedAt()).isTrue();

        // Delete the todo
        DeleteTodoRequest deleteRequest = DeleteTodoRequest.newBuilder()
            .setId(todoId)
            .build();

        byte[] deleteResponse = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(USER_ID)))
            .post()
            .uri("/connect/todos.v1.TodosService/DeleteTodo")
            .contentType(APPLICATION_PROTO)
            .bodyValue(deleteRequest.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        DeleteTodoResponse deleteResp = DeleteTodoResponse.parseFrom(deleteResponse);
        assertThat(deleteResp.getSuccess()).isTrue();

        // Verify deleted - list should not contain the todo
        byte[] finalList = webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(USER_ID)))
            .post()
            .uri("/connect/todos.v1.TodosService/GetTodos")
            .contentType(APPLICATION_PROTO)
            .bodyValue(GetTodosRequest.getDefaultInstance().toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        GetTodosResponse finalResponse = GetTodosResponse.parseFrom(finalList);
        assertThat(finalResponse.getTodosList())
            .noneMatch(t -> t.getId().equals(todoId));
    }
}
