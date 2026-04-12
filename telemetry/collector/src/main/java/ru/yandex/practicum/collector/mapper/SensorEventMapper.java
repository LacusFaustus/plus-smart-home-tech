package ru.yandex.practicum.collector.mapper;

import lombok.experimental.UtilityClass;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Instant;

@UtilityClass
public class SensorEventMapper {

    public SensorEventAvro mapToAvro(SensorEventProto event) {
        Instant instant = Instant.ofEpochSecond(
                event.getTimestamp().getSeconds(),
                event.getTimestamp().getNanos()
        );

        Object payload = null;

        if (event.hasMotionSensor()) {
            payload = mapMotionSensor(event.getMotionSensor());
        } else if (event.hasTemperatureSensor()) {
            payload = mapTemperatureSensor(event.getTemperatureSensor());
        } else if (event.hasLightSensor()) {
            payload = mapLightSensor(event.getLightSensor());
        } else if (event.hasClimateSensor()) {
            payload = mapClimateSensor(event.getClimateSensor());
        } else if (event.hasSwitchSensor()) {
            payload = mapSwitchSensor(event.getSwitchSensor());
        }

        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())  // hub_id -> hubId
                .setTimestamp(instant)
                .setPayload(payload)
                .build();
    }

    private ClimateSensorAvro mapClimateSensor(ClimateSensorProto proto) {
        return ClimateSensorAvro.newBuilder()
                .setTemperatureC(proto.getTemperatureC())
                .setHumidity(proto.getHumidity())
                .setCo2Level(proto.getCo2Level())
                .build();
    }

    private LightSensorAvro mapLightSensor(LightSensorProto proto) {
        return LightSensorAvro.newBuilder()
                .setLinkQuality(proto.getLinkQuality())
                .setLuminosity(proto.getLuminosity())
                .build();
    }

    private MotionSensorAvro mapMotionSensor(MotionSensorProto proto) {
        return MotionSensorAvro.newBuilder()
                .setLinkQuality(proto.getLinkQuality())
                .setMotion(proto.getMotion() != 0)  // int -> boolean
                .setVoltage(proto.getVoltage())
                .build();
    }

    private SwitchSensorAvro mapSwitchSensor(SwitchSensorProto proto) {
        return SwitchSensorAvro.newBuilder()
                .setState(proto.getState() != 0)  // int -> boolean
                .build();
    }

    private TemperatureSensorAvro mapTemperatureSensor(TemperatureSensorProto proto) {
        return TemperatureSensorAvro.newBuilder()
                .setTemperatureC(proto.getTemperatureC())
                .setTemperatureF(proto.getTemperatureF())
                .build();
    }
}