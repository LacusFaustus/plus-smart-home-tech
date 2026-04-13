package ru.yandex.practicum.analyzer.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.config.KafkaConfig;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.analyzer.mapper.AvroToEntityMapper;
import ru.yandex.practicum.analyzer.repository.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {
    private final KafkaConsumer<String, HubEventAvro> hubEventConsumer;
    private final KafkaConfig kafkaConfig;
    private final SensorRepository sensorRepository;
    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    private volatile boolean running = true;

    @Override
    public void run() {
        hubEventConsumer.subscribe(List.of(kafkaConfig.getTopics().getHubEvents()));
        log.info("=== HubEventProcessor STARTED ===");
        log.info("Subscribed to topic: {}", kafkaConfig.getTopics().getHubEvents());

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running) {
                ConsumerRecords<String, HubEventAvro> records =
                        hubEventConsumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                log.info("Received {} hub event records", records.count());

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    try {
                        log.info("Processing hub event: offset={}, key={}", record.offset(), record.key());
                        processHubEvent(record.value());
                        log.info("Hub event processed successfully");
                    } catch (Exception e) {
                        log.error("ERROR processing hub event: {}", e.getMessage(), e);
                        return;
                    }
                }

                hubEventConsumer.commitSync();
                log.debug("Committed hub events");
            }
        } catch (Exception e) {
            log.error("Error in HubEventProcessor: {}", e.getMessage(), e);
        } finally {
            try {
                hubEventConsumer.commitSync();
            } catch (Exception e) {
                log.error("Error during final commit", e);
            }
            hubEventConsumer.close();
            log.info("HubEventConsumer closed");
        }
    }

    @Transactional
    public void processHubEvent(HubEventAvro event) {
        String hubId = event.getHubId().toString();
        Object payload = event.getPayload();

        log.info("=== PROCESSING HUB EVENT ===");
        log.info("hubId={}, payloadType={}", hubId, payload.getClass().getSimpleName());

        if (payload instanceof DeviceAddedEventAvro) {
            handleDeviceAdded(hubId, (DeviceAddedEventAvro) payload);
        } else if (payload instanceof DeviceRemovedEventAvro) {
            handleDeviceRemoved(hubId, (DeviceRemovedEventAvro) payload);
        } else if (payload instanceof ScenarioAddedEventAvro) {
            handleScenarioAdded(hubId, (ScenarioAddedEventAvro) payload);
        } else if (payload instanceof ScenarioRemovedEventAvro) {
            handleScenarioRemoved(hubId, (ScenarioRemovedEventAvro) payload);
        } else {
            log.warn("Unknown payload type: {}", payload.getClass());
        }
    }

    private void handleDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        String sensorId = event.getId().toString();
        log.info("📌 DEVICE_ADDED: hubId={}, sensorId={}, type={}", hubId, sensorId, event.getType());

        Sensor sensor = Sensor.builder()
                .id(sensorId)
                .hubId(hubId)
                .build();

        sensorRepository.save(sensor);
        log.info("✅ Sensor saved: id={}, hubId={}", sensorId, hubId);
    }

    private void handleDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        String sensorId = event.getId().toString();
        log.info("🗑️ DEVICE_REMOVED: hubId={}, sensorId={}", hubId, sensorId);

        sensorRepository.deleteByHubIdAndId(hubId, sensorId);
        log.info("✅ Sensor deleted: id={}, hubId={}", sensorId, hubId);
    }

    @Transactional
    public void handleScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName().toString();
        log.info("📜 SCENARIO_ADDED: hubId={}, name={}", hubId, scenarioName);
        log.info("  Conditions count: {}", event.getConditions().size());
        log.info("  Actions count: {}", event.getActions().size());

        // Delete existing scenario
        scenarioRepository.findByHubIdAndName(hubId, scenarioName).ifPresent(existing -> {
            log.info("  Removing existing scenario with id={}", existing.getId());
            scenarioRepository.delete(existing);
            scenarioRepository.flush();
        });

        // Create new scenario
        Scenario scenario = Scenario.builder()
                .hubId(hubId)
                .name(scenarioName)
                .conditions(new HashMap<>())
                .actions(new HashMap<>())
                .build();

        // Save scenario first to get ID
        scenario = scenarioRepository.save(scenario);
        log.info("  Created scenario with id={}", scenario.getId());

        // Add conditions
        for (ScenarioConditionAvro conditionAvro : event.getConditions()) {
            String sensorId = conditionAvro.getSensorId().toString();
            log.info("  Processing condition: sensorId={}, type={}, operation={}, value={}",
                    sensorId, conditionAvro.getType(), conditionAvro.getOperation(), conditionAvro.getValue());

            Sensor sensor = sensorRepository.findByIdAndHubId(sensorId, hubId)
                    .orElseGet(() -> {
                        log.info("    Sensor not found, creating new: id={}", sensorId);
                        return sensorRepository.save(Sensor.builder().id(sensorId).hubId(hubId).build());
                    });

            Condition condition = AvroToEntityMapper.mapToCondition(conditionAvro);
            condition = conditionRepository.save(condition);
            log.info("    Condition saved with id={}", condition.getId());

            scenario.getConditions().put(sensor, condition);
        }

        // Add actions
        for (DeviceActionAvro actionAvro : event.getActions()) {
            String sensorId = actionAvro.getSensorId().toString();
            log.info("  Processing action: sensorId={}, type={}, value={}",
                    sensorId, actionAvro.getType(), actionAvro.getValue());

            Sensor sensor = sensorRepository.findByIdAndHubId(sensorId, hubId)
                    .orElseGet(() -> {
                        log.info("    Sensor not found, creating new: id={}", sensorId);
                        return sensorRepository.save(Sensor.builder().id(sensorId).hubId(hubId).build());
                    });

            Action action = AvroToEntityMapper.mapToAction(actionAvro);
            action = actionRepository.save(action);
            log.info("    Action saved with id={}", action.getId());

            scenario.getActions().put(sensor, action);
        }

        scenarioRepository.save(scenario);
        log.info("✅ Scenario saved: hubId={}, name={}, conditions={}, actions={}",
                hubId, scenarioName, scenario.getConditions().size(), scenario.getActions().size());
    }

    private void handleScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        String scenarioName = event.getName().toString();
        log.info("🗑️ SCENARIO_REMOVED: hubId={}, name={}", hubId, scenarioName);

        scenarioRepository.deleteByHubIdAndName(hubId, scenarioName);
        log.info("✅ Scenario deleted");
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down HubEventProcessor...");
        running = false;
        hubEventConsumer.wakeup();
    }
}