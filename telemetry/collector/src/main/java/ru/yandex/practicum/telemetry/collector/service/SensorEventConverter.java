package ru.yandex.practicum.telemetry.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.collector.model.internal.*;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.SensorEventType;

@Component
@Slf4j
public class SensorEventConverter {

    public SensorEventAvro toAvro(SensorEventInternal event) {
        SensorEventAvro.Builder builder = SensorEventAvro.newBuilder();
        builder.setId(event.getId());
        builder.setHubId(event.getHubId());
        builder.setTimestamp(event.getTimestamp().toEpochMilli());

        log.info("Converting sensor event: type={}, id={}", event.getType(), event.getId());

        if (event.getType() == SensorEventType.CLIMATE_SENSOR_EVENT) {
            ClimateSensorEventInternal climateEvent = (ClimateSensorEventInternal) event;
            ClimateSensorAvro climateAvro = ClimateSensorAvro.newBuilder()
                    .setTemperatureC(climateEvent.getTemperatureC())
                    .setHumidity(climateEvent.getHumidity())
                    .setCo2Level(climateEvent.getCo2Level())
                    .build();
            builder.setPayload(climateAvro);
            log.info("Converted ClimateSensorEvent: temp={}, humidity={}, co2={}",
                    climateEvent.getTemperatureC(), climateEvent.getHumidity(), climateEvent.getCo2Level());

        } else if (event.getType() == SensorEventType.LIGHT_SENSOR_EVENT) {
            LightSensorEventInternal lightEvent = (LightSensorEventInternal) event;
            LightSensorAvro lightAvro = LightSensorAvro.newBuilder()
                    .setLinkQuality(lightEvent.getLinkQuality())
                    .setLuminosity(lightEvent.getLuminosity())
                    .build();
            builder.setPayload(lightAvro);
            log.info("Converted LightSensorEvent: luminosity={}", lightEvent.getLuminosity());

        } else if (event.getType() == SensorEventType.MOTION_SENSOR_EVENT) {
            MotionSensorEventInternal motionEvent = (MotionSensorEventInternal) event;
            MotionSensorAvro motionAvro = MotionSensorAvro.newBuilder()
                    .setLinkQuality(motionEvent.getLinkQuality())
                    .setMotion(motionEvent.isMotion())
                    .setVoltage(motionEvent.getVoltage())
                    .build();
            builder.setPayload(motionAvro);
            log.info("Converted MotionSensorEvent: motion={}", motionEvent.isMotion());

        } else if (event.getType() == SensorEventType.SWITCH_SENSOR_EVENT) {
            SwitchSensorEventInternal switchEvent = (SwitchSensorEventInternal) event;
            SwitchSensorAvro switchAvro = SwitchSensorAvro.newBuilder()
                    .setState(switchEvent.isState())
                    .build();
            builder.setPayload(switchAvro);
            log.info("Converted SwitchSensorEvent: state={}", switchEvent.isState());

        } else if (event.getType() == SensorEventType.TEMPERATURE_SENSOR_EVENT) {
            TemperatureSensorEventInternal tempEvent = (TemperatureSensorEventInternal) event;
            TemperatureSensorAvro tempAvro = TemperatureSensorAvro.newBuilder()
                    .setTemperatureC(tempEvent.getTemperatureC())
                    .setTemperatureF(tempEvent.getTemperatureF())
                    .build();
            builder.setPayload(tempAvro);
            log.info("Converted TemperatureSensorEvent: tempC={}", tempEvent.getTemperatureC());

        } else {
            log.error("Unknown sensor event type: {}", event.getType());
            throw new IllegalArgumentException("Unknown sensor event type: " + event.getType());
        }

        return builder.build();
    }
}