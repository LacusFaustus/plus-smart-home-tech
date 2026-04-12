package ru.yandex.practicum.collector.mapper;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

        int intValue = extractValue(proto, type);

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
     * Для MOTION (тег 4) - через getValue()
     * Для остальных (тег 5) - через ручной парсинг байтов сообщения
     */
    private int extractValue(ScenarioConditionProto proto, ConditionTypeProto type) {
        // Для MOTION (тег 4) - работает стандартный метод
        if (type == ConditionTypeProto.MOTION) {
            int value = proto.getValue();
            log.debug("MOTION value extracted via getValue(): {}", value);
            return value;
        }

        // Для остальных типов - ручной парсинг байтов сообщения
        try {
            byte[] bytes = proto.toByteArray();
            int parsedValue = parseValueFromTag5(bytes);
            if (parsedValue != 0) {
                log.debug("Value for {} extracted via manual parsing (tag 5): {}", type, parsedValue);
                return parsedValue;
            }
        } catch (IOException e) {
            log.warn("Failed to manually parse value from tag 5: {}", e.getMessage());
        }

        log.warn("Could not extract value for condition of type {}. Returning 0.", type);
        return 0;
    }

    /**
     * Парсит байты Protobuf сообщения и извлекает значение поля с тегом 5.
     * Формат Protobuf: [tag] [type] [value]
     * Тег кодируется как (field_number << 3) | wire_type
     * wire_type для int32 = 0
     */
    private int parseValueFromTag5(byte[] bytes) throws IOException {
        CodedInputStream input = CodedInputStream.newInstance(bytes);

        while (!input.isAtEnd()) {
            int tag = input.readTag();
            int fieldNumber = tag >>> 3;

            if (fieldNumber == 5) {
                // Читаем int32 значение
                return input.readInt32();
            } else {
                // Пропускаем другие поля
                input.skipField(tag);
            }
        }

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