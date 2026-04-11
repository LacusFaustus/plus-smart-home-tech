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
@Slf4j
@RequiredArgsConstructor
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final SensorRepository sensorRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    @Transactional
    public void processHubEvent(HubEventAvro event) {
        Object payload = event.getPayload();
        String hubId = event.getHubId();

        log.info("Processing hub event: hubId={}, payloadType={}",
                hubId, payload.getClass().getSimpleName());

        if (payload instanceof DeviceAddedEventAvro) {
            processDeviceAdded(hubId, (DeviceAddedEventAvro) payload);
        } else if (payload instanceof DeviceRemovedEventAvro) {
            processDeviceRemoved(hubId, (DeviceRemovedEventAvro) payload);
        } else if (payload instanceof ScenarioAddedEventAvro) {
            processScenarioAdded(hubId, (ScenarioAddedEventAvro) payload);
        } else if (payload instanceof ScenarioRemovedEventAvro) {
            processScenarioRemoved(hubId, (ScenarioRemovedEventAvro) payload);
        } else {
            log.warn("Unknown hub event payload type: {}", payload.getClass().getName());
        }
    }

    private void processDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        String sensorId = event.getId();

        if (sensorRepository.existsById(sensorId)) {
            log.debug("Sensor already exists: {}", sensorId);
            return;
        }

        Sensor sensor = Sensor.builder()
                .id(sensorId)
                .hubId(hubId)
                .build();
        sensorRepository.save(sensor);
        log.info("✅ Device added: sensorId={}, hubId={}", sensorId, hubId);
    }

    private void processDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        String sensorId = event.getId();
        Optional<Sensor> sensor = sensorRepository.findByIdAndHubId(sensorId, hubId);

        if (sensor.isPresent()) {
            sensorRepository.delete(sensor.get());
            log.info("✅ Device removed: sensorId={}, hubId={}", sensorId, hubId);
        } else {
            log.debug("Device not found for removal: sensorId={}, hubId={}", sensorId, hubId);
        }
    }

    @Transactional
    public void processScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName();

        Optional<Scenario> existingScenario = scenarioRepository.findByHubIdAndName(hubId, scenarioName);

        Scenario scenario;
        if (existingScenario.isPresent()) {
            scenario = existingScenario.get();
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

        Scenario savedScenario = scenarioRepository.save(scenario);

        // Сохраняем условия
        for (ScenarioConditionAvro conditionAvro : event.getConditions()) {
            String sensorId = conditionAvro.getSensorId();

            // Создаем сенсор если не существует
            if (!sensorRepository.existsById(sensorId)) {
                Sensor newSensor = Sensor.builder()
                        .id(sensorId)
                        .hubId(hubId)
                        .build();
                sensorRepository.save(newSensor);
                log.debug("Created new sensor for condition: sensorId={}", sensorId);
            }

            // Извлекаем значение
            Integer conditionValue = null;
            Object rawValue = conditionAvro.getValue();
            log.info("Raw condition value for sensorId={}: type={}, value={}",
                    sensorId, rawValue != null ? rawValue.getClass() : "null", rawValue);

            if (rawValue != null) {
                if (rawValue instanceof Integer) {
                    conditionValue = (Integer) rawValue;
                } else if (rawValue instanceof Long) {
                    conditionValue = ((Long) rawValue).intValue();
                } else if (rawValue instanceof Number) {
                    conditionValue = ((Number) rawValue).intValue();
                }
            }

            Condition condition = Condition.builder()
                    .type(conditionAvro.getType().toString())
                    .operation(conditionAvro.getOperation().toString())
                    .value(conditionValue)
                    .build();
            condition = conditionRepository.save(condition);
            savedScenario.getConditions().put(sensorId, condition);
            log.info("Added condition for sensorId={}: type={}, operation={}, value={}",
                    sensorId, condition.getType(), condition.getOperation(), condition.getValue());
        }

        // Сохраняем действия
        for (DeviceActionAvro actionAvro : event.getActions()) {
            String sensorId = actionAvro.getSensorId();

            if (!sensorRepository.existsById(sensorId)) {
                Sensor newSensor = Sensor.builder()
                        .id(sensorId)
                        .hubId(hubId)
                        .build();
                sensorRepository.save(newSensor);
                log.debug("Created new sensor for action: sensorId={}", sensorId);
            }

            Object rawValue = actionAvro.getValue();
            log.info("Raw action value for sensorId={}: type={}, value={}",
                    sensorId, rawValue != null ? rawValue.getClass() : "null", rawValue);

            Integer actionValue = null;
            if (rawValue != null) {
                if (rawValue instanceof Integer) {
                    actionValue = (Integer) rawValue;
                } else if (rawValue instanceof Long) {
                    actionValue = ((Long) rawValue).intValue();
                } else if (rawValue instanceof Number) {
                    actionValue = ((Number) rawValue).intValue();
                }
            }

            Action action = Action.builder()
                    .type(actionAvro.getType().toString())
                    .value(actionValue)
                    .build();
            action = actionRepository.save(action);
            savedScenario.getActions().put(sensorId, action);
            log.info("Added action for sensorId={}: type={}, value={}",
                    sensorId, action.getType(), action.getValue());
        }

        scenarioRepository.save(savedScenario);
        log.info("✅ Scenario saved: hubId={}, name={}, conditions={}, actions={}",
                hubId, scenarioName, savedScenario.getConditions().size(), savedScenario.getActions().size());
    }

    private void processScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        String scenarioName = event.getName();
        scenarioRepository.deleteByHubIdAndName(hubId, scenarioName);
        log.info("✅ Scenario removed: hubId={}, name={}", hubId, scenarioName);
    }

    @Transactional(readOnly = true)
    public List<Scenario> getScenariosByHubId(String hubId) {
        return scenarioRepository.findByHubId(hubId);
    }
}