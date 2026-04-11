package ru.yandex.practicum.telemetry.analyzer.service;

import com.google.protobuf.Timestamp;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Action;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class HubRouterClientService {

    @GrpcClient("hub-router")
    private HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    public void sendAction(Scenario scenario, String sensorId, Action action, Instant timestamp) {
        try {
            log.info("Sending action to Hub Router: scenario={}, sensorId={}, type={}, value={}",
                    scenario.getName(), sensorId, action.getType(), action.getValue());

            DeviceActionRequest request = DeviceActionRequest.newBuilder()
                    .setHubId(scenario.getHubId())
                    .setScenarioName(scenario.getName())
                    .setAction(DeviceAction.newBuilder()
                            .setSensorId(ByteString.copyFromUtf8(sensorId))
                            .setType(mapActionType(action.getType()))
                            .setValue(action.getValue() != null ? action.getValue() : 0)
                            .build())
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(timestamp.getEpochSecond())
                            .setNanos(timestamp.getNano())
                            .build())
                    .build();

            hubRouterClient.handleDeviceAction(request);
            log.info("✅ Action sent successfully: scenario={}, sensorId={}", scenario.getName(), sensorId);

        } catch (StatusRuntimeException e) {
            log.error("❌ gRPC error sending action to Hub Router: scenario={}, sensorId={}, status={}",
                    scenario.getName(), sensorId, e.getStatus(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending action to Hub Router: scenario={}, sensorId={}",
                    scenario.getName(), sensorId, e);
        }
    }

    private ActionType mapActionType(String type) {
        if (type == null) {
            log.warn("Action type is null, using ACTIVATE as default");
            return ActionType.ACTIVATE;
        }

        switch (type.toUpperCase()) {
            case "ACTIVATE":
                return ActionType.ACTIVATE;
            case "DEACTIVATE":
                return ActionType.DEACTIVATE;
            case "INVERSE":
                return ActionType.INVERSE;
            case "SET_VALUE":
                return ActionType.SET_VALUE;
            default:
                log.warn("Unknown action type: {}, using ACTIVATE as default", type);
                return ActionType.ACTIVATE;
        }
    }
}