package com.template.todos;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class TodoService {
    private final TodoRepository repository;

    public TodoService(TodoRepository repository) {
        this.repository = repository;
    }

    public Mono<Todo> createTodo(String userId, String title) {
        Todo todo = new Todo(null, userId, title, null, Instant.now());
        return repository.save(todo);
    }

    public Flux<Todo> getTodos(String userId) {
        return repository.findAllByUserId(userId);
    }

    public Mono<Todo> completeTodo(String userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
            .flatMap(todo -> {
                if (todo.completedAt() != null) {
                    return Mono.just(todo);
                }
                return repository.save(todo.withCompletedAt(Instant.now()));
            });
    }

    public Mono<Void> deleteTodo(String userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
            .flatMap(repository::delete);
    }
}
