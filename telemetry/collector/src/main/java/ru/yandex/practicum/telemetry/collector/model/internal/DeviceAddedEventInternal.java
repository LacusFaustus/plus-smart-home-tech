package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.HubEventType;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceAddedEventInternal extends HubEventInternal {
    private String id;
    private DeviceType deviceType;

    @Override
    public HubEventType getType() {
        return HubEventType.DEVICE_ADDED;
    }
}