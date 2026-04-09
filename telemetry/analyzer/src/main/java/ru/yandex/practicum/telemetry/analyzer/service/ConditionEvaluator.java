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
        log.debug("Evaluating scenario: {}", scenario.getName());

        Map<String, SensorStateAvro> sensorsState = snapshot.getSensorsState();

        for (Map.Entry<String, Condition> entry : scenario.getConditions().entrySet()) {
            String sensorId = entry.getKey();
            Condition condition = entry.getValue();

            SensorStateAvro sensorState = sensorsState.get(sensorId);

            if (sensorState == null) {
                log.debug("Condition NOT satisfied: sensor '{}' not found in snapshot", sensorId);
                return false;
            }

            boolean satisfied = evaluateCondition(condition, sensorState.getData());

            if (!satisfied) {
                log.debug("Condition NOT satisfied: sensorId={}, type={}, operation={}, expected={}",
                        sensorId, condition.getType(), condition.getOperation(), condition.getValue());
                return false;
            }
        }

        log.debug("All conditions satisfied for scenario: {}", scenario.getName());
        return true;
    }

    private boolean evaluateCondition(Condition condition, Object sensorData) {
        String type = condition.getType();
        String operation = condition.getOperation();
        Integer expectedValue = condition.getValue();

        if (expectedValue == null) {
            return evaluateBooleanCondition(type, operation, sensorData);
        } else {
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
            log.warn("Unexpected sensor data type for boolean condition: {}", sensorData.getClass().getSimpleName());
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
                    return false;
                }
                break;
            case "HUMIDITY":
                if (sensorData instanceof ClimateSensorAvro) {
                    actualValue = ((ClimateSensorAvro) sensorData).getHumidity();
                } else {
                    return false;
                }
                break;
            case "CO2LEVEL":
                if (sensorData instanceof ClimateSensorAvro) {
                    actualValue = ((ClimateSensorAvro) sensorData).getCo2Level();
                } else {
                    return false;
                }
                break;
            case "LUMINOSITY":
                if (sensorData instanceof LightSensorAvro) {
                    actualValue = ((LightSensorAvro) sensorData).getLuminosity();
                } else {
                    return false;
                }
                break;
            default:
                return false;
        }

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