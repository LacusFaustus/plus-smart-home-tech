package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.HubEventType;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceRemovedEventInternal extends HubEventInternal {
    private String id;

    @Override
    public HubEventType getType() {
        return HubEventType.DEVICE_REMOVED;
    }
}