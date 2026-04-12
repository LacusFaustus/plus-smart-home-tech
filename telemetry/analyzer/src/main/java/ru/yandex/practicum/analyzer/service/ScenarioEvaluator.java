package ru.yandex.practicum.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.util.Utf8;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.*;
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
        log.info("Проверка сценария '{}' для хаба {} (условий: {})",
                scenario.getName(), snapshot.getHubId(), scenario.getConditions().size());

        for (Map.Entry<Sensor, Condition> entry : scenario.getConditions().entrySet()) {
            Sensor sensor = entry.getKey();
            Condition condition = entry.getValue();

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

    private boolean evaluateCondition(Condition condition, SensorStateAvro state) {
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
        if (data == null) {
            return null;
        }

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

    private SensorStateAvro getSensorState(SensorsSnapshotAvro snapshot, String sensorId) {
        Map<CharSequence, SensorStateAvro> states = snapshot.getSensorsState();
        if (states == null || states.isEmpty()) {
            log.debug("Снапшот для хаба {} не содержит состояний датчиков", snapshot.getHubId());
            return null;
        }

        // Логируем все ключи для отладки
        log.debug("Ключи в снапшоте: {}", states.keySet());
        log.debug("Ищем датчик с ID: {}", sensorId);

        // Прямой поиск по строке (Avro использует Utf8)
        SensorStateAvro state = states.get(sensorId);
        if (state != null) {
            log.debug("Датчик {} найден по прямому ключу", sensorId);
            return state;
        }

        // Поиск через Utf8
        Utf8 utf8Key = new Utf8(sensorId);
        state = states.get(utf8Key);
        if (state != null) {
            log.debug("Датчик {} найден через Utf8", sensorId);
            return state;
        }

        // Поиск перебором с преобразованием ключей в строки
        for (Map.Entry<CharSequence, SensorStateAvro> entry : states.entrySet()) {
            String keyStr = entry.getKey().toString();
            if (keyStr.equals(sensorId)) {
                log.debug("Датчик {} найден перебором с toString()", sensorId);
                return entry.getValue();
            }
        }

        log.debug("Датчик {} не найден в снапшоте", sensorId);
        return null;
    }
}