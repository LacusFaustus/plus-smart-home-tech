package ru.yandex.practicum.telemetry.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.collector.model.*;

@Component
@Slf4j
public class SensorEventConverter {

    public SensorEventAvro toAvro(SensorEvent event) {
        SensorEventAvro.Builder builder = SensorEventAvro.newBuilder();
        builder.setId(event.getId());
        builder.setHubId(event.getHubId());
        builder.setTimestamp(event.getTimestamp().toEpochMilli());

        switch (event.getType()) {
            case CLIMATE_SENSOR_EVENT:
                ClimateSensorEvent climateEvent = (ClimateSensorEvent) event;
                ClimateSensorAvro climateAvro = ClimateSensorAvro.newBuilder()
                        .setTemperatureC(climateEvent.getTemperatureC())
                        .setHumidity(climateEvent.getHumidity())
                        .setCo2Level(climateEvent.getCo2Level())
                        .build();
                builder.setPayload(climateAvro);
                log.debug("Converted ClimateSensorEvent to Avro: id={}", event.getId());
                break;

            case LIGHT_SENSOR_EVENT:
                LightSensorEvent lightEvent = (LightSensorEvent) event;
                LightSensorAvro lightAvro = LightSensorAvro.newBuilder()
                        .setLinkQuality(lightEvent.getLinkQuality())
                        .setLuminosity(lightEvent.getLuminosity())
                        .build();
                builder.setPayload(lightAvro);
                log.debug("Converted LightSensorEvent to Avro: id={}", event.getId());
                break;

            case MOTION_SENSOR_EVENT:
                MotionSensorEvent motionEvent = (MotionSensorEvent) event;
                MotionSensorAvro motionAvro = MotionSensorAvro.newBuilder()
                        .setLinkQuality(motionEvent.getLinkQuality())
                        .setMotion(motionEvent.isMotion())
                        .setVoltage(motionEvent.getVoltage())
                        .build();
                builder.setPayload(motionAvro);
                log.debug("Converted MotionSensorEvent to Avro: id={}", event.getId());
                break;

            case SWITCH_SENSOR_EVENT:
                SwitchSensorEvent switchEvent = (SwitchSensorEvent) event;
                SwitchSensorAvro switchAvro = SwitchSensorAvro.newBuilder()
                        .setState(switchEvent.isState())
                        .build();
                builder.setPayload(switchAvro);
                log.debug("Converted SwitchSensorEvent to Avro: id={}", event.getId());
                break;

            case TEMPERATURE_SENSOR_EVENT:
                TemperatureSensorEvent tempEvent = (TemperatureSensorEvent) event;
                TemperatureSensorAvro tempAvro = TemperatureSensorAvro.newBuilder()
                        .setTemperatureC(tempEvent.getTemperatureC())
                        .setTemperatureF(tempEvent.getTemperatureF())
                        .build();
                builder.setPayload(tempAvro);
                log.debug("Converted TemperatureSensorEvent to Avro: id={}", event.getId());
                break;

            default:
                log.error("Unknown sensor event type: {}", event.getType());
                throw new IllegalArgumentException("Unknown sensor event type: " + event.getType());
        }

        return builder.build();
    }
}