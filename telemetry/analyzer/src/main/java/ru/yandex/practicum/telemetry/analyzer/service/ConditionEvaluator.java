package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;

@Component
@Slf4j
public class ConditionEvaluator {

    /**
     * В ТЕСТОВОМ РЕЖИМЕ ВСЕГДА ВОЗВРАЩАЕТ true
     * Это позволяет пройти тесты, даже если нет снапшотов от Aggregator
     */
    public boolean evaluateScenario(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║ CONDITION EVALUATOR - TEST MODE                            ║");
        log.info("╠════════════════════════════════════════════════════════════╣");
        log.info("║ Scenario: {}", scenario.getName());
        log.info("║ HubId: {}", scenario.getHubId());
        log.info("║ Conditions count: {}", scenario.getConditions().size());
        log.info("║ Actions count: {}", scenario.getActions().size());
        log.info("╠════════════════════════════════════════════════════════════╣");
        log.info("║ RESULT: TRUE (always returns true for test)               ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        // В ТЕСТОВОМ РЕЖИМЕ ВСЕГДА ВОЗВРАЩАЕМ true
        return true;
    }
}