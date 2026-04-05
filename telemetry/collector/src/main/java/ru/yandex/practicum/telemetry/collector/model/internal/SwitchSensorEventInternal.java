package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.SensorEventType;

@Data
@EqualsAndHashCode(callSuper = true)
public class SwitchSensorEventInternal extends SensorEventInternal {
    private boolean state;

    @Override
    public SensorEventType getType() {
        return SensorEventType.SWITCH_SENSOR_EVENT;
    }
}