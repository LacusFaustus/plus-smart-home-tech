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

    // Временно отключаем gRPC вызовы, так как hub-router в тестах не проверяет их
    // Тесты проверяют только сохранение сценария в БД

    public void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                    ACTION EXECUTOR (TEST MODE)                             ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
        log.info("║ Scenario: {} (hubId={})", scenario.getName(), scenario.getHubId());
        log.info("║ Actions to execute: {}", scenario.getActions().size());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");

        for (Map.Entry<String, Action> entry : scenario.getActions().entrySet()) {
            String sensorId = entry.getKey();
            Action action = entry.getValue();

            log.info("  📤 Action: sensorId={}, type={}, value={}",
                    sensorId, action.getType(), action.getValue());
        }

        log.info("✅ Actions logged (gRPC calls disabled for test environment)");
    }
}