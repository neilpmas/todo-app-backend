package com.template.todos;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TodoRepository extends ReactiveCrudRepository<Todo, UUID> {
    Flux<Todo> findAllByUserId(String userId);
    Mono<Todo> findByIdAndUserId(UUID id, String userId);
}
