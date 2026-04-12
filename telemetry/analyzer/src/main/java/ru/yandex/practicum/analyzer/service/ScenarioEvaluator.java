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
        log.info("Evaluating scenario '{}' for hub {} (conditions: {})",
                scenario.getName(), snapshot.getHubId(), scenario.getConditions().size());

        for (Map.Entry<Sensor, Condition> entry : scenario.getConditions().entrySet()) {
            Sensor sensor = entry.getKey();
            Condition condition = entry.getValue();

            SensorStateAvro state = getSensorState(snapshot, sensor.getId());
            if (state == null) {
                log.info("  ❌ Sensor {} not found in snapshot", sensor.getId());
                return false;
            }

            boolean conditionMet = evaluateCondition(condition, state);
            if (!conditionMet) {
                log.info("  ❌ Condition NOT met: sensor={}, type={}, operation={}, expected={}",
                        sensor.getId(), condition.getType(), condition.getOperation(), condition.getValue());
                return false;
            } else {
                log.info("  ✅ Condition met: sensor={}, type={}, operation={}, expected={}",
                        sensor.getId(), condition.getType(), condition.getOperation(), condition.getValue());
            }
        }

        log.info("✅ Scenario '{}' activated! Executing {} actions",
                scenario.getName(), scenario.getActions().size());
        return true;
    }

    private boolean evaluateCondition(Condition condition, SensorStateAvro state) {
        String type = condition.getType();
        String operation = condition.getOperation();
        Integer expectedValue = condition.getValue();

        Object data = state.getData();
        Integer actualValue = extractValue(data, type);

        log.debug("Condition evaluation: type={}, actual={}, expected={}, operation={}",
                type, actualValue, expectedValue, operation);

        if (actualValue == null) {
            log.warn("Could not extract value for type {}", type);
            return false;
        }

        BiPredicate<Integer, Integer> predicate = OPERATIONS.get(operation);
        if (predicate == null) {
            log.warn("Unknown operation: {}", operation);
            return false;
        }

        boolean result = predicate.test(actualValue, expectedValue);
        log.debug("Condition result: {}", result);
        return result;
    }

    private Integer extractValue(Object data, String type) {
        if (data == null) {
            return null;
        }

        log.debug("Extracting value from data type: {}, for condition type: {}",
                data.getClass().getSimpleName(), type);

        return switch (type) {
            case "MOTION" -> {
                if (data instanceof MotionSensorAvro motion) {
                    yield motion.getMotion() ? 1 : 0;
                }
                log.warn("Data is not MotionSensorAvro: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "SWITCH" -> {
                if (data instanceof SwitchSensorAvro switchSensor) {
                    yield switchSensor.getState() ? 1 : 0;
                }
                log.warn("Data is not SwitchSensorAvro: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "TEMPERATURE" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getTemperatureC();
                } else if (data instanceof TemperatureSensorAvro temp) {
                    yield temp.getTemperatureC();
                }
                log.warn("Data does not contain temperature: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "LUMINOSITY" -> {
                if (data instanceof LightSensorAvro light) {
                    yield light.getLuminosity();
                }
                log.warn("Data is not LightSensorAvro: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "CO2LEVEL" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getCo2Level();
                }
                log.warn("Data does not contain CO2: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "HUMIDITY" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getHumidity();
                }
                log.warn("Data does not contain humidity: {}", data.getClass().getSimpleName());
                yield null;
            }
            default -> {
                log.warn("Unknown condition type: {}", type);
                yield null;
            }
        };
    }

    private SensorStateAvro getSensorState(SensorsSnapshotAvro snapshot, String sensorId) {
        Map<CharSequence, SensorStateAvro> states = snapshot.getSensorsState();
        if (states == null || states.isEmpty()) {
            log.debug("Snapshot for hub {} has no sensor states", snapshot.getHubId());
            return null;
        }

        // Try direct string lookup
        SensorStateAvro state = states.get(sensorId);
        if (state != null) {
            log.debug("Sensor {} found by direct key", sensorId);
            return state;
        }

        // Try Utf8 lookup
        Utf8 utf8Key = new Utf8(sensorId);
        state = states.get(utf8Key);
        if (state != null) {
            log.debug("Sensor {} found via Utf8", sensorId);
            return state;
        }

        // Try iteration
        for (Map.Entry<CharSequence, SensorStateAvro> entry : states.entrySet()) {
            String keyStr = entry.getKey().toString();
            if (keyStr.equals(sensorId)) {
                log.debug("Sensor {} found via iteration", sensorId);
                return entry.getValue();
            }
        }

        log.debug("Sensor {} not found in snapshot", sensorId);
        return null;
    }
}