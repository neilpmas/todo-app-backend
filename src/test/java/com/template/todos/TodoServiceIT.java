package com.template.todos;

import com.template.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TodoServiceIT extends AbstractIntegrationTest {

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll().block();
    }

    @Test
    void testCreateTodo() {
        String userId = "user-1";
        String title = "Test Todo";

        StepVerifier.create(todoService.createTodo(userId, title))
            .assertNext(todo -> {
                assertThat(todo.id()).isNotNull();
                assertThat(todo.userId()).isEqualTo(userId);
                assertThat(todo.title()).isEqualTo(title);
                assertThat(todo.completedAt()).isNull();
                assertThat(todo.createdAt()).isNotNull();
            })
            .verifyComplete();
    }

    @Test
    void testGetTodos() {
        String userId1 = "user-1";
        String userId2 = "user-2";

        todoService.createTodo(userId1, "Todo 1").block();
        todoService.createTodo(userId1, "Todo 2").block();
        todoService.createTodo(userId2, "Todo 3").block();

        StepVerifier.create(todoService.getTodos(userId1))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void testCompleteTodo() {
        String userId = "user-1";
        Todo todo = todoService.createTodo(userId, "Todo").block();

        StepVerifier.create(todoService.completeTodo(userId, todo.id()))
            .assertNext(updatedTodo -> {
                assertThat(updatedTodo.completedAt()).isNotNull();
            })
            .verifyComplete();
    }

    @Test
    void testDeleteTodo() {
        String userId = "user-1";
        Todo todo = todoService.createTodo(userId, "Todo").block();

        StepVerifier.create(todoService.deleteTodo(userId, todo.id()))
            .verifyComplete();

        StepVerifier.create(todoRepository.findById(todo.id()))
            .expectNextCount(0)
            .verifyComplete();
    }

    @Test
    void testCannotTouchOtherUsersTodo() {
        String userId1 = "user-1";
        String userId2 = "user-2";
        Todo todo = todoService.createTodo(userId1, "Todo").block();

        StepVerifier.create(todoService.completeTodo(userId2, todo.id()))
            .expectNextCount(0)
            .verifyComplete();

        StepVerifier.create(todoService.deleteTodo(userId2, todo.id()))
            .verifyComplete();

        StepVerifier.create(todoRepository.findById(todo.id()))
            .expectNextCount(1)
            .verifyComplete();
    }
}
