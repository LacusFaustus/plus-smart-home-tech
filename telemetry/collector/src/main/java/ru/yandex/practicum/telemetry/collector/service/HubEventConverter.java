package ru.yandex.practicum.telemetry.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.collector.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HubEventConverter {

    public HubEventAvro toAvro(HubEvent event) {
        HubEventAvro.Builder builder = HubEventAvro.newBuilder();
        builder.setHubId(event.getHubId());
        builder.setTimestamp(event.getTimestamp().toEpochMilli());

        switch (event.getType()) {
            case DEVICE_ADDED:
                DeviceAddedEvent deviceAddedEvent = (DeviceAddedEvent) event;
                DeviceAddedEventAvro deviceAddedAvro = DeviceAddedEventAvro.newBuilder()
                        .setId(deviceAddedEvent.getId())
                        .setType(mapDeviceType(deviceAddedEvent.getDeviceType()))
                        .build();
                builder.setPayload(deviceAddedAvro);
                log.debug("Converted DeviceAddedEvent to Avro: id={}", deviceAddedEvent.getId());
                break;

            case DEVICE_REMOVED:
                DeviceRemovedEvent deviceRemovedEvent = (DeviceRemovedEvent) event;
                DeviceRemovedEventAvro deviceRemovedAvro = DeviceRemovedEventAvro.newBuilder()
                        .setId(deviceRemovedEvent.getId())
                        .build();
                builder.setPayload(deviceRemovedAvro);
                log.debug("Converted DeviceRemovedEvent to Avro: id={}", deviceRemovedEvent.getId());
                break;

            case SCENARIO_ADDED:
                ScenarioAddedEvent scenarioAddedEvent = (ScenarioAddedEvent) event;
                List<ScenarioConditionAvro> conditionsAvro = scenarioAddedEvent.getConditions().stream()
                        .map(this::mapCondition)
                        .collect(Collectors.toList());
                List<DeviceActionAvro> actionsAvro = scenarioAddedEvent.getActions().stream()
                        .map(this::mapAction)
                        .collect(Collectors.toList());

                ScenarioAddedEventAvro scenarioAddedAvro = ScenarioAddedEventAvro.newBuilder()
                        .setName(scenarioAddedEvent.getName())
                        .setConditions(conditionsAvro)
                        .setActions(actionsAvro)
                        .build();
                builder.setPayload(scenarioAddedAvro);
                log.debug("Converted ScenarioAddedEvent to Avro: name={}", scenarioAddedEvent.getName());
                break;

            case SCENARIO_REMOVED:
                ScenarioRemovedEvent scenarioRemovedEvent = (ScenarioRemovedEvent) event;
                ScenarioRemovedEventAvro scenarioRemovedAvro = ScenarioRemovedEventAvro.newBuilder()
                        .setName(scenarioRemovedEvent.getName())
                        .build();
                builder.setPayload(scenarioRemovedAvro);
                log.debug("Converted ScenarioRemovedEvent to Avro: name={}", scenarioRemovedEvent.getName());
                break;

            default:
                log.error("Unknown hub event type: {}", event.getType());
                throw new IllegalArgumentException("Unknown hub event type: " + event.getType());
        }

        return builder.build();
    }

    private DeviceTypeAvro mapDeviceType(DeviceType deviceType) {
        switch (deviceType) {
            case MOTION_SENSOR:
                return DeviceTypeAvro.MOTION_SENSOR;
            case TEMPERATURE_SENSOR:
                return DeviceTypeAvro.TEMPERATURE_SENSOR;
            case LIGHT_SENSOR:
                return DeviceTypeAvro.LIGHT_SENSOR;
            case CLIMATE_SENSOR:
                return DeviceTypeAvro.CLIMATE_SENSOR;
            case SWITCH_SENSOR:
                return DeviceTypeAvro.SWITCH_SENSOR;
            default:
                throw new IllegalArgumentException("Unknown device type: " + deviceType);
        }
    }

    private ScenarioConditionAvro mapCondition(ScenarioCondition condition) {
        ScenarioConditionAvro.Builder builder = ScenarioConditionAvro.newBuilder();
        builder.setSensorId(condition.getSensorId());
        builder.setType(mapConditionType(condition.getType()));
        builder.setOperation(mapConditionOperation(condition.getOperation()));

        if (condition.getValue() != null) {
            builder.setValue(condition.getValue());
        }

        return builder.build();
    }

    private ConditionTypeAvro mapConditionType(ConditionType type) {
        switch (type) {
            case MOTION:
                return ConditionTypeAvro.MOTION;
            case LUMINOSITY:
                return ConditionTypeAvro.LUMINOSITY;
            case SWITCH:
                return ConditionTypeAvro.SWITCH;
            case TEMPERATURE:
                return ConditionTypeAvro.TEMPERATURE;
            case CO2LEVEL:
                return ConditionTypeAvro.CO2LEVEL;
            case HUMIDITY:
                return ConditionTypeAvro.HUMIDITY;
            default:
                throw new IllegalArgumentException("Unknown condition type: " + type);
        }
    }

    private ConditionOperationAvro mapConditionOperation(ConditionOperation operation) {
        switch (operation) {
            case EQUALS:
                return ConditionOperationAvro.EQUALS;
            case GREATER_THAN:
                return ConditionOperationAvro.GREATER_THAN;
            case LOWER_THAN:
                return ConditionOperationAvro.LOWER_THAN;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private DeviceActionAvro mapAction(DeviceAction action) {
        DeviceActionAvro.Builder builder = DeviceActionAvro.newBuilder();
        builder.setSensorId(action.getSensorId());
        builder.setType(mapActionType(action.getType()));

        if (action.getValue() != null) {
            builder.setValue(action.getValue());
        }

        return builder.build();
    }

    private ActionTypeAvro mapActionType(ActionType type) {
        switch (type) {
            case ACTIVATE:
                return ActionTypeAvro.ACTIVATE;
            case DEACTIVATE:
                return ActionTypeAvro.DEACTIVATE;
            case INVERSE:
                return ActionTypeAvro.INVERSE;
            case SET_VALUE:
                return ActionTypeAvro.SET_VALUE;
            default:
                throw new IllegalArgumentException("Unknown action type: " + type);
        }
    }
}