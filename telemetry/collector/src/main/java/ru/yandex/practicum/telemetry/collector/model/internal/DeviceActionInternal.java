package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.ActionType;

@Data
public class DeviceActionInternal {
    private String sensorId;
    private ActionType type;
    private Integer value;
}