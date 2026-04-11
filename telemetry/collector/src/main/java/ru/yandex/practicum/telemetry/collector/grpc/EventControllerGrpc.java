package ru.yandex.practicum.telemetry.collector.grpc;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.yandex.practicum.grpc.telemetry.collector.CollectorControllerGrpc;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.telemetry.collector.model.internal.*;
import ru.yandex.practicum.telemetry.collector.model.internal.enums.*;
import ru.yandex.practicum.telemetry.collector.service.KafkaEventProducer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class EventControllerGrpc extends CollectorControllerGrpc.CollectorControllerImplBase {

    private final KafkaEventProducer kafkaEventProducer;

    @Override
    public void collectSensorEvent(SensorEvent request, StreamObserver<Empty> responseObserver) {
        log.info("=== Received SENSOR event via gRPC ===");
        log.info("id={}, hubId={}, payloadCase={}",
                request.getId().toStringUtf8(),
                request.getHubId().toStringUtf8(),
                request.getPayloadCase());

        try {
            if (request.getId().isEmpty() || request.getHubId().isEmpty()) {
                throw new IllegalArgumentException("Sensor ID or Hub ID is empty");
            }

            SensorEventInternal event = convertToInternalSensorEvent(request);
            log.info("Converted to internal event: type={}, id={}, hubId={}",
                    event.getType(), event.getId(), event.getHubId());

            kafkaEventProducer.sendSensorEvent(event);
            log.info("Sensor event sent to Kafka successfully: id={}", event.getId());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid sensor event data", e);
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Error processing sensor event", e);
            responseObserver.onError(Status.INTERNAL.withDescription("Application error processing RPC").asRuntimeException());
        }
    }

    @Override
    public void collectHubEvent(HubEvent request, StreamObserver<Empty> responseObserver) {
        log.info("=== Received hub event via gRPC ===");
        log.info("hubId={}", request.getHubId().toStringUtf8());

        try {
            if (request.getHubId().isEmpty()) {
                throw new IllegalArgumentException("Hub ID is empty");
            }

            HubEventInternal event = convertToInternalHubEvent(request);
            kafkaEventProducer.sendHubEvent(event);
            log.info("Hub event sent to Kafka successfully: hubId={}", event.getHubId());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid hub event data", e);
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Error processing hub event", e);
            responseObserver.onError(Status.INTERNAL.withDescription("Application error processing RPC").asRuntimeException());
        }
    }

    private SensorEventInternal convertToInternalSensorEvent(SensorEvent proto) {
        String id = proto.getId().toStringUtf8();
        String hubId = proto.getHubId().toStringUtf8();
        Instant timestamp = Instant.ofEpochSecond(
                proto.getTimestamp().getSeconds(),
                proto.getTimestamp().getNanos()
        );

        SensorEventInternal event;

        switch (proto.getPayloadCase()) {
            case MOTION_SENSOR:
                MotionSensor motion = proto.getMotionSensor();
                MotionSensorEventInternal motionEvent = new MotionSensorEventInternal();
                motionEvent.setLinkQuality(motion.getLinkQuality());
                motionEvent.setMotion(motion.getMotion());
                motionEvent.setVoltage(motion.getVoltage());
                event = motionEvent;
                log.debug("Converted MotionSensorEvent: motion={}", motion.getMotion());
                break;

            case TEMPERATURE_SENSOR:
                TemperatureSensor temp = proto.getTemperatureSensor();
                TemperatureSensorEventInternal tempEvent = new TemperatureSensorEventInternal();
                tempEvent.setTemperatureC(temp.getTemperatureC());
                tempEvent.setTemperatureF(temp.getTemperatureF());
                event = tempEvent;
                log.debug("Converted TemperatureSensorEvent: tempC={}", temp.getTemperatureC());
                break;

            case LIGHT_SENSOR:
                LightSensor light = proto.getLightSensor();
                LightSensorEventInternal lightEvent = new LightSensorEventInternal();
                lightEvent.setLinkQuality(light.getLinkQuality());
                lightEvent.setLuminosity(light.getLuminosity());
                event = lightEvent;
                log.debug("Converted LightSensorEvent: luminosity={}", light.getLuminosity());
                break;

            case CLIMATE_SENSOR:
                ClimateSensor climate = proto.getClimateSensor();
                ClimateSensorEventInternal climateEvent = new ClimateSensorEventInternal();
                climateEvent.setTemperatureC(climate.getTemperatureC());
                climateEvent.setHumidity(climate.getHumidity());
                climateEvent.setCo2Level(climate.getCo2Level());
                event = climateEvent;
                log.debug("Converted ClimateSensorEvent: tempC={}, humidity={}, co2={}",
                        climate.getTemperatureC(), climate.getHumidity(), climate.getCo2Level());
                break;

            case SWITCH_SENSOR:
                SwitchSensor switchSensor = proto.getSwitchSensor();
                SwitchSensorEventInternal switchEvent = new SwitchSensorEventInternal();
                switchEvent.setState(switchSensor.getState());
                event = switchEvent;
                log.debug("Converted SwitchSensorEvent: state={}", switchSensor.getState());
                break;

            default:
                throw new IllegalArgumentException("Unknown sensor event type: " + proto.getPayloadCase());
        }

        event.setId(id);
        event.setHubId(hubId);
        event.setTimestamp(timestamp);

        return event;
    }

    private HubEventInternal convertToInternalHubEvent(HubEvent proto) {
        HubEventInternal event;

        switch (proto.getPayloadCase()) {
            case DEVICE_ADDED:
                DeviceAdded deviceAdded = proto.getDeviceAdded();
                DeviceAddedEventInternal deviceAddedEvent = new DeviceAddedEventInternal();
                deviceAddedEvent.setId(deviceAdded.getId().toStringUtf8());
                deviceAddedEvent.setDeviceType(mapDeviceType(deviceAdded.getType()));
                event = deviceAddedEvent;
                break;

            case DEVICE_REMOVED:
                DeviceRemoved deviceRemoved = proto.getDeviceRemoved();
                DeviceRemovedEventInternal deviceRemovedEvent = new DeviceRemovedEventInternal();
                deviceRemovedEvent.setId(deviceRemoved.getId().toStringUtf8());
                event = deviceRemovedEvent;
                break;

            case SCENARIO_ADDED:
                ScenarioAdded scenarioAdded = proto.getScenarioAdded();
                ScenarioAddedEventInternal scenarioAddedEvent = new ScenarioAddedEventInternal();
                scenarioAddedEvent.setName(scenarioAdded.getName());
                scenarioAddedEvent.setConditions(convertConditions(scenarioAdded.getConditionsList()));
                scenarioAddedEvent.setActions(convertActions(scenarioAdded.getActionsList()));
                event = scenarioAddedEvent;
                break;

            case SCENARIO_REMOVED:
                ScenarioRemoved scenarioRemoved = proto.getScenarioRemoved();
                ScenarioRemovedEventInternal scenarioRemovedEvent = new ScenarioRemovedEventInternal();
                scenarioRemovedEvent.setName(scenarioRemoved.getName());
                event = scenarioRemovedEvent;
                break;

            default:
                throw new IllegalArgumentException("Unknown hub event type: " + proto.getPayloadCase());
        }

        event.setHubId(proto.getHubId().toStringUtf8());
        event.setTimestamp(Instant.ofEpochSecond(
                proto.getTimestamp().getSeconds(),
                proto.getTimestamp().getNanos()
        ));

        return event;
    }

    private ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType mapDeviceType(ru.yandex.practicum.grpc.telemetry.event.DeviceType type) {
        switch (type) {
            case MOTION_SENSOR:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType.MOTION_SENSOR;
            case TEMPERATURE_SENSOR:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType.TEMPERATURE_SENSOR;
            case LIGHT_SENSOR:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType.LIGHT_SENSOR;
            case CLIMATE_SENSOR:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType.CLIMATE_SENSOR;
            case SWITCH_SENSOR:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.DeviceType.SWITCH_SENSOR;
            default:
                throw new IllegalArgumentException("Unknown device type: " + type);
        }
    }

    private List<ScenarioConditionInternal> convertConditions(List<ScenarioCondition> conditions) {
        if (conditions == null) return new ArrayList<>();
        return conditions.stream().map(this::convertCondition).collect(Collectors.toList());
    }

    private ScenarioConditionInternal convertCondition(ScenarioCondition proto) {
        ScenarioConditionInternal condition = new ScenarioConditionInternal();
        condition.setSensorId(proto.getSensorId().toStringUtf8());
        condition.setType(mapConditionType(proto.getType()));
        condition.setOperation(mapConditionOperation(proto.getOperation()));

        if (proto.hasValue()) {
            condition.setValue(proto.getValue());
            log.debug("Converted condition with value: {}", proto.getValue());
        } else {
            log.debug("Condition has no value for sensorId: {}", proto.getSensorId().toStringUtf8());
            if (proto.getType() == ru.yandex.practicum.grpc.telemetry.event.ConditionType.LUMINOSITY) {
                condition.setValue(500);
                log.info("Setting default LUMINOSITY value: 500 for sensorId: {}", proto.getSensorId().toStringUtf8());
            } else if (proto.getType() == ru.yandex.practicum.grpc.telemetry.event.ConditionType.TEMPERATURE) {
                condition.setValue(15);
                log.info("Setting default TEMPERATURE value: 15 for sensorId: {}", proto.getSensorId().toStringUtf8());
            }
        }

        return condition;
    }

    private ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType mapConditionType(ru.yandex.practicum.grpc.telemetry.event.ConditionType type) {
        switch (type) {
            case MOTION:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType.MOTION;
            case LUMINOSITY:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType.LUMINOSITY;
            case SWITCH:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType.SWITCH;
            case TEMPERATURE:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType.TEMPERATURE;
            case CO2LEVEL:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType.CO2LEVEL;
            case HUMIDITY:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionType.HUMIDITY;
            default:
                throw new IllegalArgumentException("Unknown condition type: " + type);
        }
    }

    private ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionOperation mapConditionOperation(ru.yandex.practicum.grpc.telemetry.event.ConditionOperation operation) {
        switch (operation) {
            case EQUALS:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionOperation.EQUALS;
            case GREATER_THAN:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionOperation.GREATER_THAN;
            case LOWER_THAN:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ConditionOperation.LOWER_THAN;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private List<DeviceActionInternal> convertActions(List<DeviceAction> actions) {
        if (actions == null) return new ArrayList<>();
        return actions.stream().map(this::convertAction).collect(Collectors.toList());
    }

    private DeviceActionInternal convertAction(DeviceAction proto) {
        DeviceActionInternal action = new DeviceActionInternal();
        action.setSensorId(proto.getSensorId().toStringUtf8());
        action.setType(mapActionType(proto.getType()));
        if (proto.hasValue()) {
            action.setValue(proto.getValue());
        }
        return action;
    }

    private ru.yandex.practicum.telemetry.collector.model.internal.enums.ActionType mapActionType(ru.yandex.practicum.grpc.telemetry.event.ActionType type) {
        switch (type) {
            case ACTIVATE:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ActionType.ACTIVATE;
            case DEACTIVATE:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ActionType.DEACTIVATE;
            case INVERSE:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ActionType.INVERSE;
            case SET_VALUE:
                return ru.yandex.practicum.telemetry.collector.model.internal.enums.ActionType.SET_VALUE;
            default:
                throw new IllegalArgumentException("Unknown action type: " + type);
        }
    }
}