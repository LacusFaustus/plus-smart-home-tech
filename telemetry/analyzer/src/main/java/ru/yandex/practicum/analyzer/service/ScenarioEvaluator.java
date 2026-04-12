package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.*;
import java.util.function.BiPredicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioEvaluator {

    private static final Map<String, BiPredicate<Integer, Integer>> OPERATIONS = Map.of(
            "EQUALS", Objects::equals,
            "GREATER_THAN", (v, t) -> v > t,
            "LOWER_THAN", (v, t) -> v < t
    );

    public boolean evaluateCondition(Condition condition, SensorStateAvro state) {
        String type = condition.getType();
        String operation = condition.getOperation();
        Integer expectedValue = condition.getValue();

        Object data = state.getData();
        Integer actualValue = extractValue(data, type);

        if (actualValue == null) {
            log.debug("Не удалось извлечь значение для типа {}", type);
            return false;
        }

        BiPredicate<Integer, Integer> predicate = OPERATIONS.get(operation);
        if (predicate == null) {
            log.warn("Неизвестная операция: {}", operation);
            return false;
        }

        boolean result = predicate.test(actualValue, expectedValue);
        log.debug("Проверка условия: type={}, actual={}, expected={}, operation={}, result={}",
                type, actualValue, expectedValue, operation, result);

        return result;
    }

    private Integer extractValue(Object data, String type) {
        return switch (type) {
            case "MOTION" -> {
                if (data instanceof MotionSensorAvro motion) {
                    yield motion.getMotion() ? 1 : 0;
                }
                yield null;
            }
            case "SWITCH" -> {
                if (data instanceof SwitchSensorAvro switchSensor) {
                    yield switchSensor.getState() ? 1 : 0;
                }
                yield null;
            }
            case "TEMPERATURE" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getTemperatureC();
                } else if (data instanceof TemperatureSensorAvro temp) {
                    yield temp.getTemperatureC();
                }
                yield null;
            }
            case "LUMINOSITY" -> {
                if (data instanceof LightSensorAvro light) {
                    yield light.getLuminosity();
                }
                yield null;
            }
            case "CO2LEVEL" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getCo2Level();
                }
                yield null;
            }
            case "HUMIDITY" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getHumidity();
                }
                yield null;
            }
            default -> {
                log.warn("Неизвестный тип условия: {}", type);
                yield null;
            }
        };
    }

    public boolean evaluateScenario(Scenario scenario, SensorsSnapshotAvro snapshot) {
        Map<CharSequence, SensorStateAvro> states = snapshot.getSensorsState();

        for (Map.Entry<Sensor, Condition> entry : scenario.getConditions().entrySet()) {
            Sensor sensor = entry.getKey();
            Condition condition = entry.getValue();

            SensorStateAvro state = states.get(sensor.getId());
            if (state == null) {
                log.debug("Датчик {} не найден в снапшоте", sensor.getId());
                return false;
            }

            if (!evaluateCondition(condition, state)) {
                return false;
            }
        }

        return true;
    }
}