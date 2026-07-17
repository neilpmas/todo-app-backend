package com.template.todos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository repository;

    private TodoService todoService;

    @BeforeEach
    void setUp() {
        todoService = new TodoService(repository);
    }

    @Test
    void createTodo_shouldSaveTodo() {
        String userId = "user-1";
        String title = "New Todo";
        Todo savedTodo = new Todo(UUID.randomUUID(), userId, title, null, Instant.now());
        
        when(repository.save(any(Todo.class))).thenReturn(Mono.just(savedTodo));

        StepVerifier.create(todoService.createTodo(userId, title))
            .expectNext(savedTodo)
            .verifyComplete();

        verify(repository).save(argThat(todo -> 
            todo.userId().equals(userId) && todo.title().equals(title)
        ));
    }

    @Test
    void getTodos_shouldReturnUserTodos() {
        String userId = "user-1";
        Todo todo1 = new Todo(UUID.randomUUID(), userId, "Todo 1", null, Instant.now());
        Todo todo2 = new Todo(UUID.randomUUID(), userId, "Todo 2", null, Instant.now());

        when(repository.findAllByUserId(userId)).thenReturn(Flux.just(todo1, todo2));

        StepVerifier.create(todoService.getTodos(userId))
            .expectNext(todo1, todo2)
            .verifyComplete();
    }

    @Test
    void completeTodo_shouldUpdateCompletedAt() {
        String userId = "user-1";
        UUID todoId = UUID.randomUUID();
        Todo existingTodo = new Todo(todoId, userId, "Todo", null, Instant.now());
        Todo completedTodo = existingTodo.withCompletedAt(Instant.now());

        when(repository.findByIdAndUserId(todoId, userId)).thenReturn(Mono.just(existingTodo));
        when(repository.save(any(Todo.class))).thenReturn(Mono.just(completedTodo));

        StepVerifier.create(todoService.completeTodo(userId, todoId))
            .assertNext(todo -> assertThat(todo.completedAt()).isNotNull())
            .verifyComplete();

        verify(repository).save(argThat(todo -> todo.completedAt() != null));
    }

    @Test
    void deleteTodo_shouldCallDelete() {
        String userId = "user-1";
        UUID todoId = UUID.randomUUID();
        Todo existingTodo = new Todo(todoId, userId, "Todo", null, Instant.now());

        when(repository.findByIdAndUserId(todoId, userId)).thenReturn(Mono.just(existingTodo));
        when(repository.delete(existingTodo)).thenReturn(Mono.empty());

        StepVerifier.create(todoService.deleteTodo(userId, todoId))
            .expectNext(true)
            .verifyComplete();

        verify(repository).delete(existingTodo);
    }

    @Test
    void deleteTodo_shouldReturnFalseWhenNotFound() {
        String userId = "user-1";
        UUID todoId = UUID.randomUUID();

        when(repository.findByIdAndUserId(todoId, userId)).thenReturn(Mono.empty());

        StepVerifier.create(todoService.deleteTodo(userId, todoId))
            .expectNext(false)
            .verifyComplete();

        verify(repository, never()).delete(any(Todo.class));
    }
}
