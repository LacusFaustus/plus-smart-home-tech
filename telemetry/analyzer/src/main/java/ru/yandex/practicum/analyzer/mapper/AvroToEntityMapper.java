package ru.yandex.practicum.analyzer.mapper;

import lombok.experimental.UtilityClass;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@UtilityClass
public class AvroToEntityMapper {

    public Sensor mapToSensor(DeviceAddedEventAvro event, String hubId) {
        return Sensor.builder()
                .id(event.getId().toString())
                .hubId(hubId)
                .build();
    }

    public Condition mapToCondition(ScenarioConditionAvro conditionAvro) {
        return Condition.builder()
                .type(conditionAvro.getType().name())
                .operation(conditionAvro.getOperation().name())
                .value(extractIntValue(conditionAvro.getValue()))
                .build();
    }

    public Action mapToAction(DeviceActionAvro actionAvro) {
        return Action.builder()
                .type(actionAvro.getType().name())
                .value(actionAvro.getValue())
                .build();
    }

    private Integer extractIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        log.warn("Unexpected value type for condition: {}", value.getClass());
        return null;
    }
}