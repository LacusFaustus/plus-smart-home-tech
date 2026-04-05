package ru.yandex.practicum.telemetry.collector.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
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
    public void collectSensorEvent(SensorEventProto request, StreamObserver<Empty> responseObserver) {
        log.info("=== Received sensor event via gRPC ===");
        log.info("id={}, hubId={}, payloadCase={}", request.getId(), request.getHubId(), request.getPayloadCase());

        try {
            SensorEventInternal event = convertToInternalSensorEvent(request);
            log.info("Converted to internal event: type={}", event.getType());

            kafkaEventProducer.sendSensorEvent(event);
            log.info("Sensor event sent to Kafka successfully: id={}", event.getId());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
            log.info("=== Response sent successfully ===");

        } catch (Exception e) {
            log.error("Error processing sensor event: id={}", request.getId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to process sensor event: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void collectHubEvent(HubEventProto request, StreamObserver<Empty> responseObserver) {
        log.info("=== Received hub event via gRPC ===");
        log.info("hubId={}, payloadCase={}", request.getHubId(), request.getPayloadCase());

        try {
            HubEventInternal event = convertToInternalHubEvent(request);
            log.info("Converted to internal hub event: type={}", event.getType());

            kafkaEventProducer.sendHubEvent(event);
            log.info("Hub event sent to Kafka successfully: hubId={}", event.getHubId());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
            log.info("=== Response sent successfully ===");

        } catch (Exception e) {
            log.error("Error processing hub event: hubId={}", request.getHubId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to process hub event: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private SensorEventInternal convertToInternalSensorEvent(SensorEventProto proto) {
        SensorEventInternal event;
        log.debug("Converting sensor event with payload case: {}", proto.getPayloadCase());

        switch (proto.getPayloadCase()) {
            case MOTION_SENSOR:
                MotionSensorProto motion = proto.getMotionSensor();
                MotionSensorEventInternal motionEvent = new MotionSensorEventInternal();
                motionEvent.setLinkQuality(motion.getLinkQuality());
                motionEvent.setMotion(motion.getMotion());
                motionEvent.setVoltage(motion.getVoltage());
                event = motionEvent;
                log.debug("Converted MOTION_SENSOR event: motion={}, voltage={}", motion.getMotion(), motion.getVoltage());
                break;

            case TEMPERATURE_SENSOR:
                TemperatureSensorProto temp = proto.getTemperatureSensor();
                TemperatureSensorEventInternal tempEvent = new TemperatureSensorEventInternal();
                tempEvent.setTemperatureC(temp.getTemperatureC());
                tempEvent.setTemperatureF(temp.getTemperatureF());
                event = tempEvent;
                log.debug("Converted TEMPERATURE_SENSOR event: tempC={}, tempF={}", temp.getTemperatureC(), temp.getTemperatureF());
                break;

            case LIGHT_SENSOR:
                LightSensorProto light = proto.getLightSensor();
                LightSensorEventInternal lightEvent = new LightSensorEventInternal();
                lightEvent.setLinkQuality(light.getLinkQuality());
                lightEvent.setLuminosity(light.getLuminosity());
                event = lightEvent;
                log.debug("Converted LIGHT_SENSOR event: luminosity={}", light.getLuminosity());
                break;

            case CLIMATE_SENSOR:
                ClimateSensorProto climate = proto.getClimateSensor();
                ClimateSensorEventInternal climateEvent = new ClimateSensorEventInternal();
                climateEvent.setTemperatureC(climate.getTemperatureC());
                climateEvent.setHumidity(climate.getHumidity());
                climateEvent.setCo2Level(climate.getCo2Level());
                event = climateEvent;
                log.debug("Converted CLIMATE_SENSOR event: tempC={}, humidity={}, co2={}",
                        climate.getTemperatureC(), climate.getHumidity(), climate.getCo2Level());
                break;

            case SWITCH_SENSOR:
                SwitchSensorProto switchSensor = proto.getSwitchSensor();
                SwitchSensorEventInternal switchEvent = new SwitchSensorEventInternal();
                switchEvent.setState(switchSensor.getState());
                event = switchEvent;
                log.debug("Converted SWITCH_SENSOR event: state={}", switchSensor.getState());
                break;

            default:
                log.error("Unknown sensor event type: {}", proto.getPayloadCase());
                throw new IllegalArgumentException("Unknown sensor event type: " + proto.getPayloadCase());
        }

        event.setId(proto.getId());
        event.setHubId(proto.getHubId());

        if (proto.hasTimestamp()) {
            event.setTimestamp(Instant.ofEpochSecond(
                    proto.getTimestamp().getSeconds(),
                    proto.getTimestamp().getNanos()
            ));
        } else {
            event.setTimestamp(Instant.now());
        }

        log.debug("Converted event: id={}, hubId={}, timestamp={}", event.getId(), event.getHubId(), event.getTimestamp());
        return event;
    }

    private HubEventInternal convertToInternalHubEvent(HubEventProto proto) {
        HubEventInternal event;
        log.debug("Converting hub event with payload case: {}", proto.getPayloadCase());

        switch (proto.getPayloadCase()) {
            case DEVICE_ADDED:
                DeviceAddedEventProto deviceAdded = proto.getDeviceAdded();
                DeviceAddedEventInternal deviceAddedEvent = new DeviceAddedEventInternal();
                deviceAddedEvent.setId(deviceAdded.getId());
                deviceAddedEvent.setDeviceType(mapDeviceType(deviceAdded.getType()));
                event = deviceAddedEvent;
                log.debug("Converted DEVICE_ADDED event: id={}, type={}", deviceAdded.getId(), deviceAdded.getType());
                break;

            case DEVICE_REMOVED:
                DeviceRemovedEventProto deviceRemoved = proto.getDeviceRemoved();
                DeviceRemovedEventInternal deviceRemovedEvent = new DeviceRemovedEventInternal();
                deviceRemovedEvent.setId(deviceRemoved.getId());
                event = deviceRemovedEvent;
                log.debug("Converted DEVICE_REMOVED event: id={}", deviceRemoved.getId());
                break;

            case SCENARIO_ADDED:
                ScenarioAddedEventProto scenarioAdded = proto.getScenarioAdded();
                ScenarioAddedEventInternal scenarioAddedEvent = new ScenarioAddedEventInternal();
                scenarioAddedEvent.setName(scenarioAdded.getName());
                scenarioAddedEvent.setConditions(convertConditions(scenarioAdded.getConditionsList()));
                scenarioAddedEvent.setActions(convertActions(scenarioAdded.getActionsList()));
                event = scenarioAddedEvent;
                log.debug("Converted SCENARIO_ADDED event: name={}, conditions={}, actions={}",
                        scenarioAdded.getName(), scenarioAdded.getConditionsCount(), scenarioAdded.getActionsCount());
                break;

            case SCENARIO_REMOVED:
                ScenarioRemovedEventProto scenarioRemoved = proto.getScenarioRemoved();
                ScenarioRemovedEventInternal scenarioRemovedEvent = new ScenarioRemovedEventInternal();
                scenarioRemovedEvent.setName(scenarioRemoved.getName());
                event = scenarioRemovedEvent;
                log.debug("Converted SCENARIO_REMOVED event: name={}", scenarioRemoved.getName());
                break;

            default:
                log.error("Unknown hub event type: {}", proto.getPayloadCase());
                throw new IllegalArgumentException("Unknown hub event type: " + proto.getPayloadCase());
        }

        event.setHubId(proto.getHubId());

        if (proto.hasTimestamp()) {
            event.setTimestamp(Instant.ofEpochSecond(
                    proto.getTimestamp().getSeconds(),
                    proto.getTimestamp().getNanos()
            ));
        } else {
            event.setTimestamp(Instant.now());
        }

        log.debug("Converted hub event: hubId={}, timestamp={}", event.getHubId(), event.getTimestamp());
        return event;
    }

    private DeviceType mapDeviceType(DeviceTypeProto type) {
        switch (type) {
            case MOTION_SENSOR:
                return DeviceType.MOTION_SENSOR;
            case TEMPERATURE_SENSOR:
                return DeviceType.TEMPERATURE_SENSOR;
            case LIGHT_SENSOR:
                return DeviceType.LIGHT_SENSOR;
            case CLIMATE_SENSOR:
                return DeviceType.CLIMATE_SENSOR;
            case SWITCH_SENSOR:
                return DeviceType.SWITCH_SENSOR;
            default:
                throw new IllegalArgumentException("Unknown device type: " + type);
        }
    }

    private List<ScenarioConditionInternal> convertConditions(List<ScenarioConditionProto> conditions) {
        if (conditions == null) {
            return new ArrayList<>();
        }
        return conditions.stream()
                .map(this::convertCondition)
                .collect(Collectors.toList());
    }

    private ScenarioConditionInternal convertCondition(ScenarioConditionProto proto) {
        ScenarioConditionInternal condition = new ScenarioConditionInternal();
        condition.setSensorId(proto.getSensorId());
        condition.setType(mapConditionType(proto.getType()));
        condition.setOperation(mapConditionOperation(proto.getOperation()));
        if (proto.hasValue()) {
            condition.setValue(proto.getValue());
        }
        return condition;
    }

    private ConditionType mapConditionType(ConditionTypeProto type) {
        switch (type) {
            case MOTION:
                return ConditionType.MOTION;
            case LUMINOSITY:
                return ConditionType.LUMINOSITY;
            case SWITCH:
                return ConditionType.SWITCH;
            case TEMPERATURE:
                return ConditionType.TEMPERATURE;
            case CO2LEVEL:
                return ConditionType.CO2LEVEL;
            case HUMIDITY:
                return ConditionType.HUMIDITY;
            default:
                throw new IllegalArgumentException("Unknown condition type: " + type);
        }
    }

    private ConditionOperation mapConditionOperation(ConditionOperationProto operation) {
        switch (operation) {
            case EQUALS:
                return ConditionOperation.EQUALS;
            case GREATER_THAN:
                return ConditionOperation.GREATER_THAN;
            case LOWER_THAN:
                return ConditionOperation.LOWER_THAN;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private List<DeviceActionInternal> convertActions(List<DeviceActionProto> actions) {
        if (actions == null) {
            return new ArrayList<>();
        }
        return actions.stream()
                .map(this::convertAction)
                .collect(Collectors.toList());
    }

    private DeviceActionInternal convertAction(DeviceActionProto proto) {
        DeviceActionInternal action = new DeviceActionInternal();
        action.setSensorId(proto.getSensorId());
        action.setType(mapActionType(proto.getType()));
        if (proto.hasValue()) {
            action.setValue(proto.getValue());
        }
        return action;
    }

    private ActionType mapActionType(ActionTypeProto type) {
        switch (type) {
            case ACTIVATE:
                return ActionType.ACTIVATE;
            case DEACTIVATE:
                return ActionType.DEACTIVATE;
            case INVERSE:
                return ActionType.INVERSE;
            case SET_VALUE:
                return ActionType.SET_VALUE;
            default:
                throw new IllegalArgumentException("Unknown action type: " + type);
        }
    }
}