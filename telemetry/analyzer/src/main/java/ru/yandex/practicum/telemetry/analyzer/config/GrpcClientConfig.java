package ru.yandex.practicum.telemetry.analyzer.config;

// Временно отключаем gRPC клиент для hub-router
// import net.devh.boot.grpc.client.inject.GrpcClient;
// import org.springframework.context.annotation.Configuration;
// import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

// @Configuration
public class GrpcClientConfig {
    // @GrpcClient("hub-router")
    // private HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;
    //
    // public HubRouterControllerGrpc.HubRouterControllerBlockingStub getHubRouterClient() {
    //     return hubRouterClient;
    // }
}