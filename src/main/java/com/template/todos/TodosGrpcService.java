package com.template.todos;

import com.google.protobuf.Timestamp;
import com.template.todos.v1.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

@GrpcService
public class TodosGrpcService extends TodosServiceGrpc.TodosServiceImplBase {

    private final TodoService todoService;

    public TodosGrpcService(TodoService todoService) {
        this.todoService = todoService;
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new RuntimeException("Unauthorized");
    }

    @Override
    public void createTodo(CreateTodoRequest request, StreamObserver<com.template.todos.v1.Todo> responseObserver) {
        String userId = getUserId();
        com.template.todos.Todo todo = todoService.createTodo(userId, request.getTitle()).block();
        responseObserver.onNext(mapToProto(todo));
        responseObserver.onCompleted();
    }

    @Override
    public void getTodos(GetTodosRequest request, StreamObserver<GetTodosResponse> responseObserver) {
        String userId = getUserId();
        GetTodosResponse response = GetTodosResponse.newBuilder()
            .addAllTodos(todoService.getTodos(userId)
                .map(this::mapToProto)
                .collectList()
                .block())
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void completeTodo(CompleteTodoRequest request, StreamObserver<com.template.todos.v1.Todo> responseObserver) {
        String userId = getUserId();
        com.template.todos.Todo todo = todoService.completeTodo(userId, UUID.fromString(request.getId())).block();
        if (todo != null) {
            responseObserver.onNext(mapToProto(todo));
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(new RuntimeException("Todo not found"));
        }
    }

    @Override
    public void deleteTodo(DeleteTodoRequest request, StreamObserver<DeleteTodoResponse> responseObserver) {
        String userId = getUserId();
        todoService.deleteTodo(userId, UUID.fromString(request.getId())).block();
        responseObserver.onNext(DeleteTodoResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    private com.template.todos.v1.Todo mapToProto(com.template.todos.Todo todo) {
        com.template.todos.v1.Todo.Builder builder = com.template.todos.v1.Todo.newBuilder()
            .setId(todo.id().toString())
            .setUserId(todo.userId())
            .setTitle(todo.title())
            .setCreatedAt(mapTimestamp(todo.createdAt()));

        if (todo.completedAt() != null) {
            builder.setCompletedAt(mapTimestamp(todo.completedAt()));
        }

        return builder.build();
    }

    private Timestamp mapTimestamp(Instant instant) {
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }
}
