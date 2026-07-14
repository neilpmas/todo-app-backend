package com.template.connect;

import com.template.AbstractIntegrationTest;
import com.template.todos.v1.*;
import dev.neilmason.boot.connect.test.AutoConfigureConnectTestClient;
import dev.neilmason.boot.connect.test.ConnectTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureConnectTestClient
class ConnectEndpointIT extends AbstractIntegrationTest {

    private static final String USER_ID = "integration-test-user";

    // See ConnectEndpointTest for why this WebTestClient bean is needed: springSecurity()
    // only wires into WebTestClient.MockServerSpec.apply(), not the auto-configured
    // WebTestClient.Builder that ConnectTestClientAutoConfiguration otherwise builds.
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

    private ConnectTestClient asUser() {
        return connectTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject(USER_ID)));
    }

    @Test
    void fullTodoLifecycle() {
        Todo createdTodo = asUser().call(
            TodosServiceGrpc.getCreateTodoMethod(),
            CreateTodoRequest.newBuilder().setTitle("Integration Test Todo").build());

        assertThat(createdTodo.getTitle()).isEqualTo("Integration Test Todo");
        assertThat(createdTodo.getUserId()).isEqualTo(USER_ID);
        String todoId = createdTodo.getId();

        GetTodosResponse todosResponse = asUser().call(
            TodosServiceGrpc.getGetTodosMethod(),
            GetTodosRequest.getDefaultInstance());

        assertThat(todosResponse.getTodosList())
            .anyMatch(t -> t.getId().equals(todoId));

        Todo completedTodo = asUser().call(
            TodosServiceGrpc.getCompleteTodoMethod(),
            CompleteTodoRequest.newBuilder().setId(todoId).build());

        assertThat(completedTodo.hasCompletedAt()).isTrue();

        DeleteTodoResponse deleteResp = asUser().call(
            TodosServiceGrpc.getDeleteTodoMethod(),
            DeleteTodoRequest.newBuilder().setId(todoId).build());

        assertThat(deleteResp.getSuccess()).isTrue();

        GetTodosResponse finalResponse = asUser().call(
            TodosServiceGrpc.getGetTodosMethod(),
            GetTodosRequest.getDefaultInstance());

        assertThat(finalResponse.getTodosList())
            .noneMatch(t -> t.getId().equals(todoId));
    }
}
