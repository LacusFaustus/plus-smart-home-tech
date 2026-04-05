package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.HubEventType;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScenarioRemovedEventInternal extends HubEventInternal {
    private String name;

    @Override
    public HubEventType getType() {
        return HubEventType.SCENARIO_REMOVED;
    }
}