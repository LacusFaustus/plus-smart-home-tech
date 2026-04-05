package ru.yandex.practicum.telemetry.analyzer.processor;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.config.KafkaConfig;
import ru.yandex.practicum.telemetry.analyzer.deserializer.SensorsSnapshotDeserializer;
import ru.yandex.practicum.telemetry.analyzer.model.entity.Scenario;
import ru.yandex.practicum.telemetry.analyzer.service.ActionExecutor;
import ru.yandex.practicum.telemetry.analyzer.service.ConditionEvaluator;
import ru.yandex.practicum.telemetry.analyzer.service.ScenarioService;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

@Component
@RequiredArgsConstructor
@Slf4j
public class SnapshotProcessor {

    private final KafkaConfig kafkaConfig;
    private final ScenarioService scenarioService;
    private final ConditionEvaluator conditionEvaluator;
    private final ActionExecutor actionExecutor;

    private KafkaConsumer<String, SensorsSnapshotAvro> consumer;
    private volatile boolean running = true;

    public void start() {
        initializeConsumer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook received for SnapshotProcessor");
            running = false;
            if (consumer != null) {
                consumer.wakeup();
            }
        }));

        try {
            consumer.subscribe(List.of(kafkaConfig.getTopics().getSnapshots()));
            log.info("SnapshotProcessor subscribed to topic: {}", kafkaConfig.getTopics().getSnapshots());

            while (running) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    SensorsSnapshotAvro snapshot = record.value();
                    if (snapshot == null) {
                        log.warn("Received null snapshot at offset: {}", record.offset());
                        continue;
                    }

                    log.info("Processing snapshot: hubId={}, timestamp={}, sensorsCount={}",
                            snapshot.getHubId(), snapshot.getTimestamp(), snapshot.getSensorsState().size());

                    try {
                        List<Scenario> scenarios = scenarioService.getScenariosByHubId(snapshot.getHubId());
                        log.debug("Found {} scenarios for hub {}", scenarios.size(), snapshot.getHubId());

                        for (Scenario scenario : scenarios) {
                            if (conditionEvaluator.evaluateScenario(scenario, snapshot)) {
                                actionExecutor.executeActions(scenario, snapshot);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing snapshot for hub: {}", snapshot.getHubId(), e);
                    }
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                    log.debug("Committed offsets for {} snapshot records", records.count());
                }
            }

        } catch (WakeupException e) {
            log.info("Wakeup exception received for SnapshotProcessor");
        } catch (Exception e) {
            log.error("Unexpected error in SnapshotProcessor", e);
        } finally {
            closeConsumer();
        }
    }

    private void initializeConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getConsumer().getSnapshot().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorsSnapshotDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfig.getConsumer().getSnapshot().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfig.getConsumer().getSnapshot().isEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConfig.getConsumer().getSnapshot().getMaxPollRecords());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        consumer = new KafkaConsumer<>(props);
        log.info("SnapshotProcessor consumer initialized with group.id: {}",
                kafkaConfig.getConsumer().getSnapshot().getGroupId());
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.commitSync();
                consumer.close();
                log.info("SnapshotProcessor consumer closed");
            }
        } catch (Exception e) {
            log.error("Error closing SnapshotProcessor consumer", e);
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (consumer != null) {
            consumer.wakeup();
        }
    }
}