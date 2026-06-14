package com.template.todos;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(schema = "app", name = "todos")
public record Todo(
    @Id
    UUID id,
    String userId,
    String title,
    Instant completedAt,
    Instant createdAt
) {
    public Todo withCompletedAt(Instant completedAt) {
        return new Todo(id, userId, title, completedAt, createdAt);
    }
}
