package com.template.api;

import com.template.grpc.v1.GetServerInfoRequest;
import com.template.grpc.v1.GetServerInfoResponse;
import com.template.grpc.v1.TemplateServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

@GrpcService
public class TemplateGrpcService extends TemplateServiceGrpc.TemplateServiceImplBase {

    @Value("${spring.application.version:0.0.1}")
    private String version;

    @Value("${app.environment:local}")
    private String environment;

    @Override
    public void getServerInfo(GetServerInfoRequest request,
                              StreamObserver<GetServerInfoResponse> observer) {
        observer.onNext(GetServerInfoResponse.newBuilder()
            .setVersion(version)
            .setEnvironment(environment)
            .build());
        observer.onCompleted();
    }
}
