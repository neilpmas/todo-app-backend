package com.template.todos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class TodoService {

    // Logged here rather than correlated to the incoming request id (see
    // RequestLoggingFilter's javadoc for why) -- userId + timestamp is enough to
    // answer "who did what", which was the actual gap: previously nothing logged
    // these mutations at all.
    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoRepository repository;

    public TodoService(TodoRepository repository) {
        this.repository = repository;
    }

    public Mono<Todo> createTodo(String userId, String title) {
        Todo todo = new Todo(null, userId, title, null, Instant.now());
        return repository.save(todo)
            .doOnNext(saved -> log.atInfo()
                .addKeyValue("action", "create")
                .addKeyValue("userId", userId)
                .addKeyValue("todoId", saved.id())
                .log("todo created"));
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
                return repository.save(todo.withCompletedAt(Instant.now()))
                    .doOnNext(saved -> log.atInfo()
                        .addKeyValue("action", "complete")
                        .addKeyValue("userId", userId)
                        .addKeyValue("todoId", saved.id())
                        .log("todo completed"));
            });
    }

    public Mono<Boolean> deleteTodo(String userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
            .flatMap(todo -> repository.delete(todo).thenReturn(true))
            .defaultIfEmpty(false)
            .doOnNext(deleted -> {
                if (deleted) {
                    log.atInfo()
                        .addKeyValue("action", "delete")
                        .addKeyValue("userId", userId)
                        .addKeyValue("todoId", id)
                        .log("todo deleted");
                }
            });
    }
}
