package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.analyzer.model.entity.*;
import ru.yandex.practicum.telemetry.analyzer.repository.*;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final SensorRepository sensorRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    @Transactional
    public void processHubEvent(HubEventAvro event) {
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                    SCENARIO SERVICE: PROCESSING HUB EVENT                  ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
        log.info("║ 📥 INPUT:                                                                  ║");
        log.info("║    hubId={}", event.getHubId());
        log.info("║    payloadType={}", event.getPayload().getClass().getSimpleName());
        log.info("║    timestamp={}", event.getTimestamp());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");

        Object payload = event.getPayload();

        if (payload instanceof DeviceAddedEventAvro) {
            processDeviceAdded(event.getHubId(), (DeviceAddedEventAvro) payload);
        } else if (payload instanceof DeviceRemovedEventAvro) {
            processDeviceRemoved(event.getHubId(), (DeviceRemovedEventAvro) payload);
        } else if (payload instanceof ScenarioAddedEventAvro) {
            processScenarioAdded(event.getHubId(), (ScenarioAddedEventAvro) payload);
        } else if (payload instanceof ScenarioRemovedEventAvro) {
            processScenarioRemoved(event.getHubId(), (ScenarioRemovedEventAvro) payload);
        } else {
            log.warn("UNKNOWN payload type: {}", payload.getClass().getName());
        }
    }

    private void processDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        String sensorId = event.getId();
        log.info("┌─────────────────────────────────────────────────────────────────────────┐");
        log.info("│ PROCESSING DEVICE_ADDED                                                │");
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 INPUT: hubId={}, sensorId={}, type={}", hubId, sensorId, event.getType());

        Optional<Sensor> existingSensor = sensorRepository.findById(sensorId);

        if (existingSensor.isPresent()) {
            log.info("├─────────────────────────────────────────────────────────────────────────┤");
            log.info("│ DECISION: Sensor already exists - skipping insert                       │");
            log.info("│ REASON: Sensor with id={} already registered in database               │", sensorId);
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
            return;
        }

        Sensor sensor = Sensor.builder()
                .id(sensorId)
                .hubId(hubId)
                .build();

        sensorRepository.save(sensor);
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ ✅ SENSOR SAVED                                                         │");
        log.info("│ REASON: New device added to hub - storing in database                   │");
        log.info("│ 📤 OUTPUT: id={}, hubId={}", sensorId, hubId);
        log.info("└─────────────────────────────────────────────────────────────────────────┘\n");
    }

    private void processDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        String sensorId = event.getId();
        log.info("┌─────────────────────────────────────────────────────────────────────────┐");
        log.info("│ PROCESSING DEVICE_REMOVED                                               │");
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 INPUT: hubId={}, sensorId={}", hubId, sensorId);

        Optional<Sensor> existingSensor = sensorRepository.findByIdAndHubId(sensorId, hubId);

        if (existingSensor.isEmpty()) {
            log.info("├─────────────────────────────────────────────────────────────────────────┤");
            log.info("│ DECISION: Sensor not found - nothing to delete                         │");
            log.info("│ REASON: Sensor with id={} not found in database for hubId={}          │", sensorId, hubId);
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
            return;
        }

        sensorRepository.delete(existingSensor.get());
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ ✅ SENSOR DELETED                                                        │");
        log.info("│ REASON: Device removed from hub - removing from database                 │");
        log.info("│ 📤 OUTPUT: deleted sensorId={}", sensorId);
        log.info("└─────────────────────────────────────────────────────────────────────────┘\n");
    }

    @Transactional
    public void processScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName();
        log.info("┌─────────────────────────────────────────────────────────────────────────┐");
        log.info("│ PROCESSING SCENARIO_ADDED                                               │");
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 INPUT: hubId={}, scenarioName={}", hubId, scenarioName);
        log.info("│    conditionsCount={}, actionsCount={}",
                event.getConditions().size(), event.getActions().size());

        // Логируем каждое условие
        for (ScenarioConditionAvro condition : event.getConditions()) {
            log.info("│    condition: sensorId={}, type={}, operation={}, value={}",
                    condition.getSensorId(), condition.getType(),
                    condition.getOperation(), condition.getValue());
        }

        // Логируем каждое действие
        for (DeviceActionAvro action : event.getActions()) {
            log.info("│    action: sensorId={}, type={}, value={}",
                    action.getSensorId(), action.getType(), action.getValue());
        }

        Optional<Scenario> existingScenario = scenarioRepository.findByHubIdAndName(hubId, scenarioName);

        Scenario scenario;
        if (existingScenario.isPresent()) {
            scenario = existingScenario.get();
            log.info("├─────────────────────────────────────────────────────────────────────────┤");
            log.info("│ DECISION: Updating existing scenario                                   │");
            log.info("│ REASON: Scenario with name='{}' already exists for hubId={}", scenarioName, hubId);
            log.info("│ ACTION: Clearing existing conditions and actions before update         │");
            scenario.getConditions().clear();
            scenario.getActions().clear();
        } else {
            scenario = Scenario.builder()
                    .hubId(hubId)
                    .name(scenarioName)
                    .conditions(new HashMap<>())
                    .actions(new HashMap<>())
                    .build();
            log.info("├─────────────────────────────────────────────────────────────────────────┤");
            log.info("│ DECISION: Creating new scenario                                        │");
            log.info("│ REASON: No existing scenario with name='{}' for hubId={}", scenarioName, hubId);
        }

        // Добавляем условия
        for (ScenarioConditionAvro conditionAvro : event.getConditions()) {
            String sensorId = conditionAvro.getSensorId();

            Condition condition = Condition.builder()
                    .type(conditionAvro.getType().toString())
                    .operation(conditionAvro.getOperation().toString())
                    .value(conditionAvro.getValue() instanceof Integer ? (Integer) conditionAvro.getValue() : null)
                    .build();
            condition = conditionRepository.save(condition);
            scenario.getConditions().put(sensorId, condition);
            log.info("│    ✅ Condition added for sensorId={}: type={}, operation={}, value={}",
                    sensorId, condition.getType(), condition.getOperation(), condition.getValue());
        }

        // Добавляем действия
        for (DeviceActionAvro actionAvro : event.getActions()) {
            String sensorId = actionAvro.getSensorId();

            Action action = Action.builder()
                    .type(actionAvro.getType().toString())
                    .value(actionAvro.getValue())
                    .build();
            action = actionRepository.save(action);
            scenario.getActions().put(sensorId, action);
            log.info("│    ✅ Action added for sensorId={}: type={}, value={}",
                    sensorId, action.getType(), action.getValue());
        }

        Scenario saved = scenarioRepository.save(scenario);
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ ✅ SCENARIO SAVED SUCCESSFULLY                                           │");
        log.info("│ 📤 OUTPUT: id={}, hubId={}, name={}", saved.getId(), saved.getHubId(), saved.getName());
        log.info("│    totalConditions={}, totalActions={}",
                saved.getConditions().size(), saved.getActions().size());
        log.info("└─────────────────────────────────────────────────────────────────────────┘\n");
    }

    private void processScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        String scenarioName = event.getName();
        log.info("┌─────────────────────────────────────────────────────────────────────────┐");
        log.info("│ PROCESSING SCENARIO_REMOVED                                             │");
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 INPUT: hubId={}, scenarioName={}", hubId, scenarioName);

        Optional<Scenario> existingScenario = scenarioRepository.findByHubIdAndName(hubId, scenarioName);

        if (existingScenario.isEmpty()) {
            log.info("├─────────────────────────────────────────────────────────────────────────┤");
            log.info("│ DECISION: Scenario not found - nothing to delete                       │");
            log.info("│ REASON: No scenario with name='{}' found for hubId={}", scenarioName, hubId);
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
            return;
        }

        scenarioRepository.delete(existingScenario.get());
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ ✅ SCENARIO DELETED                                                      │");
        log.info("│ REASON: Scenario removed by user - deleting from database                │");
        log.info("│ 📤 OUTPUT: deleted scenario: hubId={}, name={}", hubId, scenarioName);
        log.info("└─────────────────────────────────────────────────────────────────────────┘\n");
    }

    public List<Scenario> getScenariosByHubId(String hubId) {
        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        log.debug("Retrieved {} scenarios for hubId={}", scenarios.size(), hubId);
        return scenarios;
    }
}