package ru.yandex.practicum.collector.mapper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;

@Slf4j
@UtilityClass
public class HubEventMapper {

    // Служебные поля Protobuf, которые нужно игнорировать
    private static final Set<String> PROTOBUF_INTERNAL_FIELDS = Set.of(
            "memoizedHashCode",
            "memoizedSerializedSize",
            "SENSOR_ID_FIELD_NUMBER",
            "TYPE_FIELD_NUMBER",
            "OPERATION_FIELD_NUMBER",
            "VALUE_FIELD_NUMBER",
            "unknownFields",
            "defaultInstanceForType",
            "bitField0_",
            "bitField1_",
            "bitField2_"
    );

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
     * Поддерживает оба тега Protobuf: 4 (для MOTION) и 5 (для LUMINOSITY, TEMPERATURE, SWITCH).
     */
    private int extractValue(ScenarioConditionProto proto) {
        // Способ 1: Стандартный getValue() для тега 4 (MOTION)
        int value = proto.getValue();
        if (value != 0) {
            log.debug("Value extracted via getValue(): {}", value);
            return value;
        }

        // Отладочный вывод всех полей класса
        debugPrintAllFields(proto);

        // Способ 2: Прямой доступ к полю value_ через рефлексию для тега 5
        try {
            Field valueField = ScenarioConditionProto.class.getDeclaredField("value_");
            valueField.setAccessible(true);
            Object fieldValue = valueField.get(proto);
            if (fieldValue instanceof Integer) {
                int extractedValue = (Integer) fieldValue;
                if (extractedValue != 0) {
                    log.debug("Value extracted via reflection from 'value_': {}", extractedValue);
                    return extractedValue;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug("Could not extract value from 'value_' field: {}", e.getMessage());
        }

        // Способ 3: Поиск поля value
        try {
            Field valueField = ScenarioConditionProto.class.getDeclaredField("value");
            valueField.setAccessible(true);
            Object fieldValue = valueField.get(proto);
            if (fieldValue instanceof Integer) {
                int extractedValue = (Integer) fieldValue;
                if (extractedValue != 0) {
                    log.debug("Value extracted via reflection from 'value': {}", extractedValue);
                    return extractedValue;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug("Could not extract value from 'value' field: {}", e.getMessage());
        }

        // Способ 4: Поиск любого подходящего int-поля (исключая служебные)
        try {
            for (Field field : ScenarioConditionProto.class.getDeclaredFields()) {
                // Пропускаем служебные поля Protobuf
                if (PROTOBUF_INTERNAL_FIELDS.contains(field.getName())) {
                    continue;
                }
                // Пропускаем поля, которые явно не value
                if (field.getName().contains("FieldNumber") ||
                        field.getName().contains("bitField") ||
                        field.getName().equals("sensorId_") ||
                        field.getName().equals("type_") ||
                        field.getName().equals("operation_")) {
                    continue;
                }

                if (field.getType() == int.class || field.getType() == Integer.class) {
                    field.setAccessible(true);
                    Object fieldValue = field.get(proto);
                    if (fieldValue instanceof Integer) {
                        int extractedValue = (Integer) fieldValue;
                        // Игнорируем 0, так как это может быть значение по умолчанию
                        if (extractedValue != 0) {
                            log.debug("Value extracted via fallback reflection from field '{}': {}",
                                    field.getName(), extractedValue);
                            return extractedValue;
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            log.error("Fallback reflection failed", e);
        }

        // Если ничего не нашли, возвращаем 0
        log.warn("Could not extract value for condition of type {}. Returning 0.", proto.getType());
        return 0;
    }

    /**
     * Отладочный метод для вывода всех полей класса и их значений.
     */
    private void debugPrintAllFields(ScenarioConditionProto proto) {
        log.info("=== Debug: All fields of ScenarioConditionProto ===");
        for (Field field : ScenarioConditionProto.class.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object fieldValue = field.get(proto);
                log.info("Field: {} (type: {}) = {}", field.getName(), field.getType().getSimpleName(), fieldValue);
            } catch (IllegalAccessException e) {
                log.info("Field: {} (type: {}) = <inaccessible>", field.getName(), field.getType().getSimpleName());
            }
        }
        log.info("=== End debug ===");
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
            throw new IllegalArgumentException("Unknown device type: " + type, e);
        }
    }

    private ConditionTypeAvro mapConditionType(ConditionTypeProto type) {
        try {
            return ConditionTypeAvro.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown condition type: " + type, e);
        }
    }

    private ConditionOperationAvro mapConditionOperation(ConditionOperationProto op) {
        try {
            return ConditionOperationAvro.valueOf(op.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown condition operation: " + op, e);
        }
    }

    private ActionTypeAvro mapActionType(ActionTypeProto type) {
        try {
            return ActionTypeAvro.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown action type: " + type, e);
        }
    }
}