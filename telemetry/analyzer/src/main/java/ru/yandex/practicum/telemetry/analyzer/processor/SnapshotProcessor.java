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
@Slf4j
@RequiredArgsConstructor
public class SnapshotProcessor {

    private final KafkaConfig kafkaConfig;
    private final ScenarioService scenarioService;
    private final ConditionEvaluator conditionEvaluator;
    private final ActionExecutor actionExecutor;

    private KafkaConsumer<String, SensorsSnapshotAvro> consumer;
    private volatile boolean running = true;

    public void start() {
        log.info("=== SnapshotProcessor started ===");
        initConsumer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook received for SnapshotProcessor");
            running = false;
            if (consumer != null) {
                consumer.wakeup();
            }
        }));

        try {
            String topic = kafkaConfig.getTopics().getSnapshots();
            consumer.subscribe(List.of(topic));
            log.info("Subscribed to topic: {}", topic);

            while (running) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(Duration.ofMillis(1000));

                if (records.isEmpty()) {
                    continue;
                }

                log.info("📦 Received {} snapshot records", records.count());

                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    SensorsSnapshotAvro snapshot = record.value();
                    if (snapshot == null) {
                        log.warn("Received null snapshot at offset: {}", record.offset());
                        continue;
                    }

                    log.info("📸 Processing snapshot: hubId={}, sensorsCount={}, offset={}",
                            snapshot.getHubId(), snapshot.getSensorsState().size(), record.offset());

                    try {
                        // Получаем все сценарии для этого хаба
                        List<Scenario> scenarios = scenarioService.getScenariosByHubId(snapshot.getHubId());

                        if (scenarios.isEmpty()) {
                            log.debug("No scenarios found for hubId={}", snapshot.getHubId());
                        }

                        // Проверяем каждый сценарий
                        for (Scenario scenario : scenarios) {
                            log.info("🔍 Checking scenario: '{}' for hubId={}", scenario.getName(), snapshot.getHubId());

                            if (conditionEvaluator.evaluateScenario(scenario, snapshot)) {
                                log.info("✅ Scenario '{}' conditions satisfied, executing actions", scenario.getName());
                                actionExecutor.executeActions(scenario, snapshot);
                            } else {
                                log.info("❌ Scenario '{}' conditions NOT satisfied", scenario.getName());
                            }
                        }

                    } catch (Exception e) {
                        log.error("Error processing snapshot for hub: {}, offset: {}",
                                snapshot.getHubId(), record.offset(), e);
                        // Не падаем, продолжаем обработку следующего снапшота
                    }
                }

                // Фиксируем смещения только после успешной обработки всей пачки
                if (!records.isEmpty()) {
                    try {
                        consumer.commitSync();
                        log.info("✅ Committed offsets for {} records", records.count());
                    } catch (CommitFailedException e) {
                        log.error("Failed to commit offsets", e);
                    }
                }
            }

        } catch (WakeupException e) {
            log.info("SnapshotProcessor wakeup");
        } catch (Exception e) {
            log.error("Unexpected error in SnapshotProcessor", e);
        } finally {
            closeConsumer();
        }
    }

    private void initConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getConsumer().getSnapshot().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorsSnapshotDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfig.getConsumer().getSnapshot().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfig.getConsumer().getSnapshot().isEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConfig.getConsumer().getSnapshot().getMaxPollRecords());

        consumer = new KafkaConsumer<>(props);
        log.info("SnapshotConsumer initialized with group.id: {}",
                kafkaConfig.getConsumer().getSnapshot().getGroupId());
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.commitSync();
                consumer.close();
                log.info("SnapshotConsumer closed");
            }
        } catch (Exception e) {
            log.error("Error closing consumer", e);
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