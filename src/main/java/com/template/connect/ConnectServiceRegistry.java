package com.template.connect;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectServiceRegistry {

    private final Map<String, MethodEntry> methods = new ConcurrentHashMap<>();

    public ConnectServiceRegistry(List<BindableService> services) {
        for (BindableService service : services) {
            ServerServiceDefinition definition = service.bindService();
            for (ServerMethodDefinition<?, ?> methodDef : definition.getMethods()) {
                MethodDescriptor<?, ?> descriptor = methodDef.getMethodDescriptor();
                String fullMethodName = descriptor.getFullMethodName();
                // fullMethodName is "package.Service/MethodName"
                String[] parts = fullMethodName.split("/");
                String methodName = parts[1];

                // Find the corresponding Java method on the service class
                Method javaMethod = findServiceMethod(service, methodName);
                if (javaMethod != null) {
                    methods.put(fullMethodName, new MethodEntry(service, javaMethod, descriptor));
                }
            }
        }
    }

    private Method findServiceMethod(BindableService service, String grpcMethodName) {
        // gRPC method names are PascalCase, Java methods are camelCase
        String javaName = Character.toLowerCase(grpcMethodName.charAt(0)) + grpcMethodName.substring(1);
        for (Method m : service.getClass().getMethods()) {
            if (m.getName().equals(javaName)
                && m.getParameterCount() == 2
                && StreamObserver.class.isAssignableFrom(m.getParameterTypes()[1])) {
                return m;
            }
        }
        return null;
    }

    public MethodEntry lookup(String serviceName, String methodName) {
        return methods.get(serviceName + "/" + methodName);
    }

    public record MethodEntry(
        BindableService service,
        Method javaMethod,
        MethodDescriptor<?, ?> descriptor
    ) {}
}
