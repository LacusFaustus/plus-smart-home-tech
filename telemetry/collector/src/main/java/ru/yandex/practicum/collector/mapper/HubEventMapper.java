package ru.yandex.practicum.collector.mapper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Instant;

@Slf4j
@UtilityClass
public class HubEventMapper {

    public HubEventAvro mapToAvro(HubEventProto event) {
        Instant instant = Instant.ofEpochSecond(
                event.getTimestamp().getSeconds(),
                event.getTimestamp().getNanos()
        );

        Object payload = switch (event.getPayloadCase()) {
            case DEVICE_ADDED -> mapDeviceAdded(event.getDeviceAdded());
            case DEVICE_REMOVED -> mapDeviceRemoved(event.getDeviceRemoved());
            case SCENARIO_ADDED -> mapScenarioAdded(event.getScenarioAdded());
            case SCENARIO_REMOVED -> mapScenarioRemoved(event.getScenarioRemoved());
            default -> throw new IllegalArgumentException("Unknown hub event type: " + event.getPayloadCase());
        };

        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(instant)
                .setPayload(payload)
                .build();
    }

    private DeviceAddedEventAvro mapDeviceAdded(DeviceAddedEventProto proto) {
        return DeviceAddedEventAvro.newBuilder()
                .setId(proto.getId())
                .setType(mapDeviceType(proto.getType()))
                .build();
    }

    private DeviceRemovedEventAvro mapDeviceRemoved(DeviceRemovedEventProto proto) {
        return DeviceRemovedEventAvro.newBuilder()
                .setId(proto.getId())
                .build();
    }

    private ScenarioAddedEventAvro mapScenarioAdded(ScenarioAddedEventProto proto) {
        return ScenarioAddedEventAvro.newBuilder()
                .setName(proto.getName())
                .setConditions(proto.getConditionsList().stream()
                        .map(HubEventMapper::mapCondition)
                        .toList())
                .setActions(proto.getActionsList().stream()
                        .map(HubEventMapper::mapAction)
                        .toList())
                .build();
    }

    private ScenarioRemovedEventAvro mapScenarioRemoved(ScenarioRemovedEventProto proto) {
        return ScenarioRemovedEventAvro.newBuilder()
                .setName(proto.getName())
                .build();
    }

    private ScenarioConditionAvro mapCondition(ScenarioConditionProto proto) {
        ConditionTypeProto type = proto.getType();
        int intValue;

        // Для MOTION клиент hub-router использует тег 4, который не соответствует нашей схеме
        // Поэтому извлекаем значение через рефлексию из поля value_
        if (type == ConditionTypeProto.MOTION) {
            intValue = extractValueForMotion(proto);
        } else {
            // Для остальных типов (LUMINOSITY, TEMPERATURE, SWITCH, CO2LEVEL, HUMIDITY)
            // значение приходит с тегом 5, который соответствует нашей схеме
            intValue = proto.getValue();
        }

        Object value = intValue;

        // Для SWITCH и MOTION конвертируем int в boolean
        if (type == ConditionTypeProto.SWITCH || type == ConditionTypeProto.MOTION) {
            value = (intValue != 0);
        }

        return ScenarioConditionAvro.newBuilder()
                .setSensorId(proto.getSensorId())
                .setType(mapConditionType(type))
                .setOperation(mapConditionOperation(proto.getOperation()))
                .setValue(value)
                .build();
    }

    /**
     * Извлекает значение для MOTION через рефлексию.
     * Клиент hub-router отправляет MOTION с тегом 4, который в сгенерированном классе
     * сохраняется в поле value_ (так как это oneof поле).
     */
    private int extractValueForMotion(ScenarioConditionProto proto) {
        try {
            java.lang.reflect.Field field = proto.getClass().getDeclaredField("value_");
            field.setAccessible(true);
            Object fieldValue = field.get(proto);
            if (fieldValue instanceof Integer) {
                return (int) fieldValue;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.warn("Не удалось извлечь value для MOTION через рефлексию: {}", e.getMessage());
        }
        // Если не удалось извлечь, возвращаем 0 (значение по умолчанию)
        return 0;
    }

    private DeviceActionAvro mapAction(DeviceActionProto proto) {
        Integer value = proto.hasValue() ? proto.getValue() : null;
        return DeviceActionAvro.newBuilder()
                .setSensorId(proto.getSensorId())
                .setType(mapActionType(proto.getType()))
                .setValue(value)
                .build();
    }

    private DeviceTypeAvro mapDeviceType(DeviceTypeProto type) {
        try {
            return DeviceTypeAvro.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown device type: " + type);
        }
    }

    private ConditionTypeAvro mapConditionType(ConditionTypeProto type) {
        try {
            return ConditionTypeAvro.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown condition type: " + type);
        }
    }

    private ConditionOperationAvro mapConditionOperation(ConditionOperationProto op) {
        try {
            return ConditionOperationAvro.valueOf(op.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown condition operation: " + op);
        }
    }

    private ActionTypeAvro mapActionType(ActionTypeProto type) {
        try {
            return ActionTypeAvro.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown action type: " + type);
        }
    }
}