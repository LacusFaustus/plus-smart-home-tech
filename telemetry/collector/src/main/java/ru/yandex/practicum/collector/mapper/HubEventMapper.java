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
        int intValue = extractValue(proto);

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
     * Извлекает значение value из ScenarioConditionProto.
     * Пробует все возможные способы получения значения.
     */
    private int extractValue(ScenarioConditionProto proto) {
        // Способ 1: Стандартный getValue() (для тега 5)
        try {
            int value = proto.getValue();
            if (value != 0) {
                return value;
            }
        } catch (Exception e) {
            // Игнорируем, пробуем дальше
        }

        // Способ 2: Прямой доступ к полю value_ через рефлексию
        try {
            java.lang.reflect.Field valueField = proto.getClass().getDeclaredField("value_");
            valueField.setAccessible(true);
            Object fieldValue = valueField.get(proto);
            if (fieldValue instanceof Integer) {
                return (int) fieldValue;
            }
        } catch (Exception e) {
            // Игнорируем, пробуем дальше
        }

        // Способ 3: Поиск всех полей, которые могут содержать значение
        try {
            java.lang.reflect.Field[] fields = proto.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.getName().contains("value") || field.getName().contains("Value")) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(proto);
                    if (fieldValue instanceof Integer) {
                        int val = (int) fieldValue;
                        if (val != 0) {
                            return val;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Не удалось извлечь value через рефлексию: {}", e.getMessage());
        }

        // Если ничего не нашли, возвращаем 0
        log.warn("Не удалось извлечь value для условия типа {}", proto.getType());
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