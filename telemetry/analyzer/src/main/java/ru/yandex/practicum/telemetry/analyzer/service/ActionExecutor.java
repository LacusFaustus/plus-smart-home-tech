package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Action;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionExecutor {

    // Временно отключаем gRPC вызовы
    // private final GrpcClientConfig grpcClientConfig;

    public void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                         ACTION EXECUTOR (MOCK MODE)                        ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
        log.info("║ Executing actions for scenario:                                           ║");
        log.info("║    hubId={}", scenario.getHubId());
        log.info("║    name={}", scenario.getName());
        log.info("║    actionsCount={}", scenario.getActions().size());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");

        int actionIndex = 0;
        int totalActions = scenario.getActions().size();

        for (Map.Entry<String, Action> entry : scenario.getActions().entrySet()) {
            actionIndex++;
            String sensorId = entry.getKey();
            Action action = entry.getValue();

            log.info("┌─────────────────────────────────────────────────────────────────────────┐");
            log.info("│ ACTION {}/{} (MOCK - gRPC disabled)                                    │", actionIndex, totalActions);
            log.info("├─────────────────────────────────────────────────────────────────────────┤");
            log.info("│ ACTION DETAILS:                                                         │");
            log.info("│    sensorId={}", sensorId);
            log.info("│    type={}", action.getType());
            log.info("│    value={}", action.getValue());
            log.info("│ STATUS: Mock execution - no actual gRPC call sent                       │");
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
        }

        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║ ACTION EXECUTION COMPLETED (MOCK) - would send {}/{} actions              ║", totalActions, totalActions);
        log.info("╚════════════════════════════════════════════════════════════════════════════╝\n");
    }
}