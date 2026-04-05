package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionOperation;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType;

@Data
public class ScenarioConditionInternal {
    private String sensorId;
    private ConditionType type;
    private ConditionOperation operation;
    private Integer value;
}