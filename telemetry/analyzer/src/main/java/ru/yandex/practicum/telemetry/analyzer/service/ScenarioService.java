package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.analyzer.model.entity.*;
import ru.yandex.practicum.telemetry.analyzer.repository.*;

import java.util.List;
import java.util.HashMap;
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
        log.info("Processing hub event: hubId={}, payloadType={}",
                event.getHubId(), event.getPayload().getClass().getSimpleName());

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
            log.warn("Unknown hub event payload type: {}", payload.getClass().getName());
        }
    }

    private void processDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        String sensorId = event.getId();
        log.info("Device added: hubId={}, sensorId={}, type={}", hubId, sensorId, event.getType());

        Sensor sensor = Sensor.builder()
                .id(sensorId)
                .hubId(hubId)
                .build();
        sensorRepository.save(sensor);
        log.info("Sensor saved: {}", sensorId);
    }

    private void processDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        String sensorId = event.getId();
        log.info("Device removed: hubId={}, sensorId={}", hubId, sensorId);

        sensorRepository.findByIdAndHubId(sensorId, hubId)
                .ifPresent(sensor -> {
                    sensorRepository.delete(sensor);
                    log.info("Sensor deleted: {}", sensorId);
                });
    }

    private void processScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName();
        log.info("Scenario added: hubId={}, name={}", hubId, scenarioName);

        // Ищем существующий сценарий
        Optional<Scenario> existingScenario = scenarioRepository.findByHubIdAndName(hubId, scenarioName);

        Scenario scenario;
        if (existingScenario.isPresent()) {
            scenario = existingScenario.get();
            // Очищаем старые связи (Hibernate сам удалит при очистке коллекций)
            scenario.getConditions().clear();
            scenario.getActions().clear();
            log.info("Updating existing scenario: hubId={}, name={}", hubId, scenarioName);
        } else {
            scenario = Scenario.builder()
                    .hubId(hubId)
                    .name(scenarioName)
                    .conditions(new HashMap<>())
                    .actions(new HashMap<>())
                    .build();
            log.info("Creating new scenario: hubId={}, name={}", hubId, scenarioName);
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
            log.debug("Condition added for sensor {}: type={}, operation={}, value={}",
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
            log.debug("Action added for sensor {}: type={}, value={}",
                    sensorId, action.getType(), action.getValue());
        }

        scenarioRepository.save(scenario);
        log.info("Scenario saved: hubId={}, name={}, conditions={}, actions={}",
                hubId, scenarioName, scenario.getConditions().size(), scenario.getActions().size());
    }

    private void processScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        String scenarioName = event.getName();
        log.info("Scenario removed: hubId={}, name={}", hubId, scenarioName);

        scenarioRepository.deleteByHubIdAndName(hubId, scenarioName);
        log.info("Scenario deleted: hubId={}, name={}", hubId, scenarioName);
    }

    public List<Scenario> getScenariosByHubId(String hubId) {
        return scenarioRepository.findByHubId(hubId);
    }
}