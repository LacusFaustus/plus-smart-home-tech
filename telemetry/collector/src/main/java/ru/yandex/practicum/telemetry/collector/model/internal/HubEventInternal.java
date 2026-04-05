package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.HubEventType;

import java.time.Instant;

@Data
public abstract class HubEventInternal {
    private String hubId;
    private Instant timestamp;
    public abstract HubEventType getType();
}