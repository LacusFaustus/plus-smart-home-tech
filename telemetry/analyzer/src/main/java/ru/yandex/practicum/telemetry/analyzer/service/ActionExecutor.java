package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Action;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionExecutor {

    private final HubRouterClientService hubRouterClientService;

    public void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("Executing actions for scenario: {} (hubId={})", scenario.getName(), scenario.getHubId());

        if (scenario.getActions().isEmpty()) {
            log.warn("Scenario '{}' has no actions to execute", scenario.getName());
            return;
        }

        Instant snapshotTimestamp = Instant.ofEpochMilli(snapshot.getTimestamp());

        for (Map.Entry<String, Action> entry : scenario.getActions().entrySet()) {
            String sensorId = entry.getKey();
            Action action = entry.getValue();

            log.info("Executing action: scenario={}, sensorId={}, type={}, value={}",
                    scenario.getName(), sensorId, action.getType(), action.getValue());

            hubRouterClientService.sendAction(scenario, sensorId, action, snapshotTimestamp);
        }
    }
}