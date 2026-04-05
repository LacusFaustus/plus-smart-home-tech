package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.SensorEventType;

import java.time.Instant;

@Data
public abstract class SensorEventInternal {
    private String id;
    private String hubId;
    private Instant timestamp;
    public abstract SensorEventType getType();
}