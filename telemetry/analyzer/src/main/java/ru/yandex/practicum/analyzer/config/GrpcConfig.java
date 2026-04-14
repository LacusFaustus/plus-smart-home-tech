package ru.yandex.practicum.analyzer.config;

import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.client.inject.GrpcClientBean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

@Configuration
@GrpcClientBean(
        clazz = HubRouterControllerGrpc.HubRouterControllerBlockingStub.class,
        client = @GrpcClient("hub-router")
)
public class GrpcConfig {
}