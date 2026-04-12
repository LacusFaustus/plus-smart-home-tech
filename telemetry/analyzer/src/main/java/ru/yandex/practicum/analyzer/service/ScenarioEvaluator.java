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
                log.warn("Данные не являются MotionSensorAvro: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "SWITCH" -> {
                if (data instanceof SwitchSensorAvro switchSensor) {
                    yield switchSensor.getState() ? 1 : 0;
                }
                log.warn("Данные не являются SwitchSensorAvro: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "TEMPERATURE" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getTemperatureC();
                } else if (data instanceof TemperatureSensorAvro temp) {
                    yield temp.getTemperatureC();
                }
                log.warn("Данные не содержат температуру: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "LUMINOSITY" -> {
                if (data instanceof LightSensorAvro light) {
                    yield light.getLuminosity();
                }
                log.warn("Данные не являются LightSensorAvro: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "CO2LEVEL" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getCo2Level();
                }
                log.warn("Данные не содержат CO2: {}", data.getClass().getSimpleName());
                yield null;
            }
            case "HUMIDITY" -> {
                if (data instanceof ClimateSensorAvro climate) {
                    yield climate.getHumidity();
                }
                log.warn("Данные не содержат влажность: {}", data.getClass().getSimpleName());
                yield null;
            }
            default -> {
                log.warn("Неизвестный тип условия: {}", type);
                yield null;
            }
        };
    }

    public boolean evaluateScenario(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("Проверка сценария '{}' для хаба {} (условий: {})",
                scenario.getName(), snapshot.getHubId(), scenario.getConditions().size());

        for (Map.Entry<Sensor, Condition> entry : scenario.getConditions().entrySet()) {
            Sensor sensor = entry.getKey();
            Condition condition = entry.getValue();

            // ИСПРАВЛЕНИЕ ЗДЕСЬ: используем новый безопасный метод
            SensorStateAvro state = getSensorState(snapshot, sensor.getId());
            if (state == null) {
                log.info("  ❌ Датчик {} не найден в снапшоте", sensor.getId());
                return false;
            }

            if (!evaluateCondition(condition, state)) {
                log.info("  ❌ Условие не выполнено: датчик={}, тип={}, операция={}, ожидаемое={}",
                        sensor.getId(), condition.getType(), condition.getOperation(), condition.getValue());
                return false;
            } else {
                log.info("  ✅ Условие выполнено: датчик={}, тип={}, операция={}, ожидаемое={}",
                        sensor.getId(), condition.getType(), condition.getOperation(), condition.getValue());
            }
        }

        log.info("✅ Сценарий '{}' активирован! Выполняется {} действий",
                scenario.getName(), scenario.getActions().size());
        return true;
    }

    private SensorStateAvro getSensorState(SensorsSnapshotAvro snapshot, String sensorId) {
        Map<CharSequence, SensorStateAvro> states = snapshot.getSensorsState();
        if (states == null) {
            return null;
        }

        // Прямой поиск по ключу-строке
        SensorStateAvro state = states.get(sensorId);
        if (state != null) {
            return state;
        }

        // Если не найден, перебираем все ключи и сравниваем их строковые представления
        for (Map.Entry<CharSequence, SensorStateAvro> entry : states.entrySet()) {
            if (entry.getKey().toString().equals(sensorId)) {
                return entry.getValue();
            }
        }
        return null;
    }
}