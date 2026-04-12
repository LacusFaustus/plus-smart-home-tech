package ru.yandex.practicum.hubrouter.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;

@Slf4j
@GrpcService
public class HubRouterController extends HubRouterControllerGrpc.HubRouterControllerImplBase {

    @Override
    public void handleDeviceAction(DeviceActionRequest request, StreamObserver<Empty> responseObserver) {
        log.info("=== HUB ROUTER RECEIVED ACTION ===");
        log.info("Hub ID: {}", request.getHubId());
        log.info("Scenario: {}", request.getScenarioName());
        log.info("Action: sensorId={}, type={}, value={}",
                request.getAction().getSensorId(),
                request.getAction().getType(),
                request.getAction().getValue());
        log.info("Timestamp: {}", request.getTimestamp());
        log.info("===================================");

        // Просто логируем и возвращаем успех
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}