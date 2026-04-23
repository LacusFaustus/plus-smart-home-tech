package ru.yandex.practicum.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.util.Utf8;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

@Slf4j
@Service
public class ScenarioEvaluator {

    private static final Map<String, BiPredicate<Integer, Integer>> OPERATIONS = Map.of(
            "EQUALS", Objects::equals,
            "GREATER_THAN", (v, t) -> v > t,
            "LOWER_THAN", (v, t) -> v < t
    );

    public boolean evaluateScenario(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("=== EVALUATING SCENARIO ===");
        log.info("Scenario name: '{}'", scenario.getName());
        log.info("Hub ID from snapshot: '{}'", snapshot.getHubId());
        log.info("Hub ID from scenario: '{}'", scenario.getHubId());
        log.info("Number of conditions: {}", scenario.getConditions().size());

        // Проверяем соответствие hubId
        if (!scenario.getHubId().equals(snapshot.getHubId().toString())) {
            log.error("Hub ID mismatch! Scenario hubId={}, Snapshot hubId={}",
                    scenario.getHubId(), snapshot.getHubId());
            return false;
        }

        if (scenario.getConditions().isEmpty()) {
            log.warn("Scenario '{}' has no conditions!", scenario.getName());
            return false;
        }

        // Выводим все датчики в снапшоте для отладки
        if (snapshot.getSensorsState() != null) {
            log.info("Sensors in snapshot ({} total):", snapshot.getSensorsState().size());
            for (Map.Entry<CharSequence, SensorStateAvro> entry : snapshot.getSensorsState().entrySet()) {
                log.info("  - sensorId='{}', dataType={}",
                        entry.getKey(),
                        entry.getValue().getData().getClass().getSimpleName());
            }
        } else {
            log.warn("Snapshot has no sensors state!");
        }

        boolean allConditionsMet = true;

        for (Map.Entry<Sensor, Condition> entry : scenario.getConditions().entrySet()) {
            Sensor sensor = entry.getKey();
            Condition condition = entry.getValue();

            log.info("--- Checking condition ---");
            log.info("Sensor ID from scenario: '{}'", sensor.getId());
            log.info("Sensor hubId: '{}'", sensor.getHubId());
            log.info("Condition type: '{}'", condition.getType());
            log.info("Condition operation: '{}'", condition.getOperation());
            log.info("Condition expected value: {}", condition.getValue());

            // Get sensor state from snapshot
            SensorStateAvro state = getSensorState(snapshot, sensor.getId());

            if (state == null) {
                log.error("❌ Sensor '{}' NOT FOUND in snapshot!", sensor.getId());
                allConditionsMet = false;
                break;
            }

            log.info("✅ Sensor found in snapshot");
            log.info("Sensor state timestamp: {}", state.getTimestamp());
            log.info("Sensor data type: {}", state.getData().getClass().getSimpleName());
            log.info("Sensor data: {}", state.getData());

            boolean conditionMet = evaluateCondition(condition, state);

            if (!conditionMet) {
                log.error("❌ Condition NOT met for sensor '{}'", sensor.getId());
                allConditionsMet = false;
                break;
            } else {
                log.info("✅ Condition met for sensor '{}'", sensor.getId());
            }
        }

        if (allConditionsMet) {
            log.info("🎉 ALL conditions met! Scenario '{}' will be activated!", scenario.getName());
            log.info("Number of actions to execute: {}", scenario.getActions().size());

            // Выводим все действия для отладки
            for (Map.Entry<Sensor, Action> entry : scenario.getActions().entrySet()) {
                log.info("  Action: sensorId='{}', type='{}', value={}",
                        entry.getKey().getId(),
                        entry.getValue().getType(),
                        entry.getValue().getValue());
            }
        } else {
            log.warn("❌ Scenario '{}' NOT activated - conditions not met", scenario.getName());
        }

        return allConditionsMet;
    }

    private boolean evaluateCondition(Condition condition, SensorStateAvro state) {
        String type = condition.getType();
        String operation = condition.getOperation();
        Integer expectedValue = condition.getValue();

        Object data = state.getData();
        Integer actualValue = extractValue(data, type);

        log.info("Evaluating condition: type={}, actual={}, expected={}, operation={}",
                type, actualValue, expectedValue, operation);

        if (actualValue == null) {
            log.error("Could not extract value for type {} from data: {}", type, data);
            return false;
        }

        BiPredicate<Integer, Integer> predicate = OPERATIONS.get(operation);
        if (predicate == null) {
            log.error("Unknown operation: {}", operation);
            return false;
        }

        boolean result = predicate.test(actualValue, expectedValue);
        log.info("Evaluation result: {} ({} {} {})", result, actualValue, operation, expectedValue);
        return result;
    }

    private Integer extractValue(Object data, String type) {
        if (data == null) {
            log.error("Data is null");
            return null;
        }

        log.debug("Extracting value from data type: {}, for condition type: {}",
                data.getClass().getSimpleName(), type);

        try {
            switch (type) {
                case "MOTION":
                    if (data instanceof MotionSensorAvro motion) {
                        int result = motion.getMotion() ? 1 : 0;
                        log.debug("Motion value: {} -> {}", motion.getMotion(), result);
                        return result;
                    }
                    log.error("Data is not MotionSensorAvro: {}", data.getClass().getSimpleName());
                    return null;

                case "SWITCH":
                    if (data instanceof SwitchSensorAvro switchSensor) {
                        int result = switchSensor.getState() ? 1 : 0;
                        log.debug("Switch state: {} -> {}", switchSensor.getState(), result);
                        return result;
                    }
                    log.error("Data is not SwitchSensorAvro: {}", data.getClass().getSimpleName());
                    return null;

                case "TEMPERATURE":
                    if (data instanceof ClimateSensorAvro climate) {
                        int temp = climate.getTemperatureC();
                        log.debug("Temperature from ClimateSensor: {}C", temp);
                        return temp;
                    } else if (data instanceof TemperatureSensorAvro temp) {
                        int tempC = temp.getTemperatureC();
                        log.debug("Temperature from TemperatureSensor: {}C", tempC);
                        return tempC;
                    }
                    log.error("Data does not contain temperature: {}", data.getClass().getSimpleName());
                    return null;

                case "LUMINOSITY":
                    if (data instanceof LightSensorAvro light) {
                        int lum = light.getLuminosity();
                        log.debug("Luminosity: {}", lum);
                        return lum;
                    }
                    log.error("Data is not LightSensorAvro: {}", data.getClass().getSimpleName());
                    return null;

                case "CO2LEVEL":
                    if (data instanceof ClimateSensorAvro climate) {
                        int co2 = climate.getCo2Level();
                        log.debug("CO2 level: {}", co2);
                        return co2;
                    }
                    log.error("Data does not contain CO2: {}", data.getClass().getSimpleName());
                    return null;

                case "HUMIDITY":
                    if (data instanceof ClimateSensorAvro climate) {
                        int humidity = climate.getHumidity();
                        log.debug("Humidity: {}", humidity);
                        return humidity;
                    }
                    log.error("Data does not contain humidity: {}", data.getClass().getSimpleName());
                    return null;

                default:
                    log.error("Unknown condition type: {}", type);
                    return null;
            }
        } catch (Exception e) {
            log.error("Error extracting value: {}", e.getMessage(), e);
            return null;
        }
    }

    private SensorStateAvro getSensorState(SensorsSnapshotAvro snapshot, String sensorId) {
        Map<CharSequence, SensorStateAvro> states = snapshot.getSensorsState();
        if (states == null || states.isEmpty()) {
            log.debug("Snapshot has no sensor states");
            return null;
        }

        // Try direct string lookup
        SensorStateAvro state = states.get(sensorId);
        if (state != null) {
            log.debug("Sensor found by direct key: {}", sensorId);
            return state;
        }

        // Try Utf8 lookup
        Utf8 utf8Key = new Utf8(sensorId);
        state = states.get(utf8Key);
        if (state != null) {
            log.debug("Sensor found via Utf8: {}", sensorId);
            return state;
        }

        // Try iteration
        for (Map.Entry<CharSequence, SensorStateAvro> entry : states.entrySet()) {
            String keyStr = entry.getKey().toString();
            if (keyStr.equals(sensorId)) {
                log.debug("Sensor found via iteration: {}", sensorId);
                return entry.getValue();
            }
        }

        log.error("Sensor {} not found in snapshot. Available sensors: {}",
                sensorId, states.keySet());
        return null;
    }
}