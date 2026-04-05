package ru.yandex.practicum.telemetry.collector.model.internal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.SensorEventType;

@Data
@EqualsAndHashCode(callSuper = true)
public class LightSensorEventInternal extends SensorEventInternal {
    private int linkQuality;
    private int luminosity;

    @Override
    public SensorEventType getType() {
        return SensorEventType.LIGHT_SENSOR_EVENT;
    }
}