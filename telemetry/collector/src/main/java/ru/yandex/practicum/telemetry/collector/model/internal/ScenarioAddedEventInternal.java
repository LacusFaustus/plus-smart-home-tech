package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.HubEventType;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScenarioAddedEventInternal extends HubEventInternal {
    private String name;
    private List<ScenarioConditionInternal> conditions;
    private List<DeviceActionInternal> actions;

    @Override
    public HubEventType getType() {
        return HubEventType.SCENARIO_ADDED;
    }
}