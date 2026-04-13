package ru.yandex.practicum.analyzer.service;

import com.google.protobuf.Timestamp;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.config.KafkaConfig;
import ru.yandex.practicum.analyzer.entity.Action;
import ru.yandex.practicum.analyzer.entity.Scenario;
import ru.yandex.practicum.analyzer.entity.Sensor;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotProcessor {
    private final KafkaConsumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final KafkaConfig kafkaConfig;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioEvaluator scenarioEvaluator;

    @GrpcClient("hub-router")
    private HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    private volatile boolean running = true;

    public void start() {
        snapshotConsumer.subscribe(List.of(kafkaConfig.getTopics().getSnapshots()));
        log.info("=== SnapshotProcessor STARTED ===");
        log.info("Subscribed to topic: {}", kafkaConfig.getTopics().getSnapshots());
        log.info("gRPC client for hub-router: {}", hubRouterClient != null ? "INJECTED" : "NULL");

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running) {
                ConsumerRecords<String, SensorsSnapshotAvro> records =
                        snapshotConsumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                log.info("Received {} snapshot records", records.count());

                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    try {
                        log.info("Processing snapshot: offset={}, key={}", record.offset(), record.key());
                        processSnapshot(record.value());
                        snapshotConsumer.commitSync();
                        log.info("Snapshot processed and committed");
                    } catch (Exception e) {
                        log.error("ERROR processing snapshot: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in SnapshotProcessor: {}", e.getMessage(), e);
        } finally {
            try {
                snapshotConsumer.commitSync();
            } catch (Exception e) {
                log.error("Error during final commit", e);
            }
            snapshotConsumer.close();
            log.info("SnapshotConsumer closed");
        }
    }

    private void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId().toString();
        log.info("=== PROCESSING SNAPSHOT ===");
        log.info("hubId={}, timestamp={}, sensorsCount={}",
                hubId, snapshot.getTimestamp(),
                snapshot.getSensorsState() != null ? snapshot.getSensorsState().size() : 0);

        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        log.info("Found {} scenarios for hub {}", scenarios.size(), hubId);

        if (scenarios.isEmpty()) {
            log.warn("⚠️ No scenarios found for hub {}!", hubId);
            return;
        }

        for (Scenario scenario : scenarios) {
            log.info("--- Checking scenario: name='{}', id={}, conditions={}, actions={}",
                    scenario.getName(), scenario.getId(),
                    scenario.getConditions().size(), scenario.getActions().size());

            try {
                boolean evaluated = scenarioEvaluator.evaluateScenario(scenario, snapshot);
                log.info("Scenario '{}' evaluated to: {}", scenario.getName(), evaluated);

                if (evaluated) {
                    log.info("🎯 Scenario '{}' ACTIVATED for hub {}", scenario.getName(), hubId);
                    executeActions(scenario, snapshot);
                } else {
                    log.info("❌ Scenario '{}' NOT activated - conditions not met", scenario.getName());
                }
            } catch (Exception e) {
                log.error("Error evaluating scenario '{}': {}", scenario.getName(), e.getMessage(), e);
            }
        }
    }

    private void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("=== EXECUTING ACTIONS for scenario '{}' ===", scenario.getName());
        log.info("Hub ID: {}", scenario.getHubId());
        log.info("Number of actions: {}", scenario.getActions().size());

        if (scenario.getActions().isEmpty()) {
            log.warn("Scenario '{}' has no actions to execute!", scenario.getName());
            return;
        }

        final int maxRetries = 3;

        for (var entry : scenario.getActions().entrySet()) {
            Sensor sensor = entry.getKey();
            Action action = entry.getValue();

            log.info("Preparing action: sensorId='{}', type='{}', value={}",
                    sensor.getId(), action.getType(), action.getValue());

            ActionTypeProto actionType;
            try {
                actionType = ActionTypeProto.valueOf(action.getType());
                log.info("Action type converted to proto: {}", actionType);
            } catch (IllegalArgumentException e) {
                log.error("Unknown action type: {}", action.getType(), e);
                continue;
            }

            DeviceActionRequest request = DeviceActionRequest.newBuilder()
                    .setHubId(scenario.getHubId())
                    .setScenarioName(scenario.getName())
                    .setAction(DeviceActionProto.newBuilder()
                            .setSensorId(sensor.getId())
                            .setType(actionType)
                            .setValue(action.getValue() != null ? action.getValue() : 0)
                            .build())
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .setNanos(Instant.now().getNano())
                            .build())
                    .build();

            log.info("Sending gRPC request to hub-router:");
            log.info("  Request details: {}", request);

            // Retry logic
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    hubRouterClient.handleDeviceAction(request);
                    log.info("✅ Action sent successfully on attempt {}", attempt);
                    break;
                } catch (Exception e) {
                    log.error("Failed on attempt {}: {}", attempt, e.getMessage());
                    if (attempt == maxRetries) {
                        log.error("❌ Failed to send action after {} attempts", maxRetries, e);
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Interrupted while waiting for retry");
                            break;
                        }
                    }
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SnapshotProcessor...");
        running = false;
        snapshotConsumer.wakeup();
    }
}