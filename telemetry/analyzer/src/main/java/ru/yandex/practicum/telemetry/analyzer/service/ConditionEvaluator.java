package ru.yandex.practicum.telemetry.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Condition;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;

import java.util.Map;

@Component
@Slf4j
public class ConditionEvaluator {

    public boolean evaluateScenario(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.debug("Evaluating scenario: hubId={}, name={}", scenario.getHubId(), scenario.getName());

        Map<String, SensorStateAvro> sensorsState = snapshot.getSensorsState();

        for (Map.Entry<String, Condition> entry : scenario.getConditions().entrySet()) {
            String sensorId = entry.getKey();
            Condition condition = entry.getValue();

            SensorStateAvro sensorState = sensorsState.get(sensorId);
            if (sensorState == null) {
                log.debug("Sensor {} not found in snapshot for scenario {}", sensorId, scenario.getName());
                return false;
            }

            boolean satisfied = evaluateCondition(condition, sensorState.getData());
            log.debug("Condition for sensor {}: type={}, operation={}, value={}, satisfied={}",
                    sensorId, condition.getType(), condition.getOperation(), condition.getValue(), satisfied);

            if (!satisfied) {
                log.debug("Scenario {} not satisfied: condition failed for sensor {}",
                        scenario.getName(), sensorId);
                return false;
            }
        }

        log.info("Scenario {} satisfied for hub {}", scenario.getName(), scenario.getHubId());
        return true;
    }

    private boolean evaluateCondition(Condition condition, Object sensorData) {
        String type = condition.getType();
        String operation = condition.getOperation();
        Integer expectedValue = condition.getValue();

        if (expectedValue == null) {
            // Boolean condition (motion, switch)
            return evaluateBooleanCondition(type, operation, sensorData);
        } else {
            // Numeric condition (temperature, humidity, CO2, luminosity)
            return evaluateNumericCondition(type, operation, sensorData, expectedValue);
        }
    }

    private boolean evaluateBooleanCondition(String type, String operation, Object sensorData) {
        boolean actualValue;

        if (sensorData instanceof MotionSensorAvro) {
            actualValue = ((MotionSensorAvro) sensorData).getMotion();
        } else if (sensorData instanceof SwitchSensorAvro) {
            actualValue = ((SwitchSensorAvro) sensorData).getState();
        } else {
            log.warn("Unexpected sensor data type for boolean condition: {}", sensorData.getClass());
            return false;
        }

        if ("EQUALS".equals(operation)) {
            return actualValue;
        } else if ("GREATER_THAN".equals(operation)) {
            return actualValue;
        } else if ("LOWER_THAN".equals(operation)) {
            return !actualValue;
        }

        return false;
    }

    private boolean evaluateNumericCondition(String type, String operation, Object sensorData, int expectedValue) {
        int actualValue;

        switch (type) {
            case "TEMPERATURE":
                if (sensorData instanceof TemperatureSensorAvro) {
                    actualValue = ((TemperatureSensorAvro) sensorData).getTemperatureC();
                } else if (sensorData instanceof ClimateSensorAvro) {
                    actualValue = ((ClimateSensorAvro) sensorData).getTemperatureC();
                } else {
                    log.warn("Unexpected sensor data type for temperature: {}", sensorData.getClass());
                    return false;
                }
                break;
            case "HUMIDITY":
                if (sensorData instanceof ClimateSensorAvro) {
                    actualValue = ((ClimateSensorAvro) sensorData).getHumidity();
                } else {
                    log.warn("Unexpected sensor data type for humidity: {}", sensorData.getClass());
                    return false;
                }
                break;
            case "CO2LEVEL":
                if (sensorData instanceof ClimateSensorAvro) {
                    actualValue = ((ClimateSensorAvro) sensorData).getCo2Level();
                } else {
                    log.warn("Unexpected sensor data type for CO2: {}", sensorData.getClass());
                    return false;
                }
                break;
            case "LUMINOSITY":
                if (sensorData instanceof LightSensorAvro) {
                    actualValue = ((LightSensorAvro) sensorData).getLuminosity();
                } else {
                    log.warn("Unexpected sensor data type for luminosity: {}", sensorData.getClass());
                    return false;
                }
                break;
            default:
                log.warn("Unknown condition type: {}", type);
                return false;
        }

        log.debug("Evaluating numeric condition: type={}, operation={}, actual={}, expected={}",
                type, operation, actualValue, expectedValue);

        switch (operation) {
            case "EQUALS":
                return actualValue == expectedValue;
            case "GREATER_THAN":
                return actualValue > expectedValue;
            case "LOWER_THAN":
                return actualValue < expectedValue;
            default:
                return false;
        }
    }
}