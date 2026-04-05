package ru.yandex.practicum.telemetry.analyzer.service;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.config.GrpcClientConfig;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Action;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionExecutor {

    private final GrpcClientConfig grpcClientConfig;

    public void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("Executing actions for scenario: hubId={}, name={}", scenario.getHubId(), scenario.getName());

        HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient = grpcClientConfig.getHubRouterClient();

        for (Map.Entry<String, Action> entry : scenario.getActions().entrySet()) {
            String sensorId = entry.getKey();
            Action action = entry.getValue();

            DeviceActionRequest request = DeviceActionRequest.newBuilder()
                    .setHubId(scenario.getHubId())
                    .setScenarioName(scenario.getName())
                    .setAction(DeviceActionProto.newBuilder()
                            .setSensorId(sensorId)
                            .setTypeValue(mapActionType(action.getType()))
                            .setValue(action.getValue() != null ? action.getValue() : 0)
                            .build())
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .setNanos(Instant.now().getNano())
                            .build())
                    .build();

            try {
                hubRouterClient.handleDeviceAction(request);
                log.info("Action sent to hub-router: hubId={}, scenario={}, sensorId={}, type={}, value={}",
                        scenario.getHubId(), scenario.getName(), sensorId, action.getType(), action.getValue());
            } catch (Exception e) {
                log.error("Failed to send action to hub-router: hubId={}, scenario={}, sensorId={}",
                        scenario.getHubId(), scenario.getName(), sensorId, e);
            }
        }
    }

    private int mapActionType(String type) {
        switch (type) {
            case "ACTIVATE": return 0;
            case "DEACTIVATE": return 1;
            case "INVERSE": return 2;
            case "SET_VALUE": return 3;
            default: return 0;
        }
    }
}