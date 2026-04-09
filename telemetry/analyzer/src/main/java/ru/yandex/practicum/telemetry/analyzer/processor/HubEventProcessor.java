package ru.yandex.practicum.telemetry.analyzer.processor;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.config.KafkaConfig;
import ru.yandex.practicum.telemetry.analyzer.deserializer.HubEventDeserializer;
import ru.yandex.practicum.telemetry.analyzer.service.ScenarioService;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class HubEventProcessor implements Runnable {

    private final KafkaConfig kafkaConfig;
    private final ScenarioService scenarioService;
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    public void awaitReady() throws InterruptedException {
        readyLatch.await(30, TimeUnit.SECONDS);
    }

    private KafkaConsumer<String, HubEventAvro> consumer;
    private volatile boolean running = true;

    @Override
    public void run() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           HUB EVENT PROCESSOR STARTED                      ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        initializeConsumer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("🛑 Shutdown hook received for HubEventProcessor");
            running = false;
            if (consumer != null) {
                consumer.wakeup();
            }
        }));

        try {
            consumer.subscribe(List.of(kafkaConfig.getTopics().getHubs()));
            log.info("📡 HubEventProcessor subscribed to topic: {}", kafkaConfig.getTopics().getHubs());

            int assignmentRetries = 0;
            while (consumer.assignment().isEmpty() && assignmentRetries < 30) {
                consumer.poll(Duration.ofMillis(100));
                assignmentRetries++;
            }
            log.info("✅ Assigned partitions: {}", consumer.assignment());

            readyLatch.countDown();
            log.info("✅ HubEventProcessor is READY to receive messages");

            while (running) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(Duration.ofMillis(1000));

                if (!records.isEmpty()) {
                    log.info("📦 RECEIVED {} hub event records from Kafka", records.count());
                }

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    HubEventAvro event = record.value();
                    if (event == null) {
                        log.warn("⚠️ Received null hub event at offset: {}", record.offset());
                        continue;
                    }

                    log.debug("Processing hub event: hubId={}, offset={}, partition={}",
                            event.getHubId(), record.offset(), record.partition());

                    try {
                        scenarioService.processHubEvent(event);
                    } catch (Exception e) {
                        log.error("❌ Error processing hub event for hubId: {}", event.getHubId(), e);
                    }
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                    log.info("✅ Committed offsets for {} hub event records", records.count());
                }
            }

        } catch (WakeupException e) {
            log.info("⚠️ Wakeup exception received for HubEventProcessor");
        } catch (Exception e) {
            log.error("❌ Unexpected error in HubEventProcessor", e);
        } finally {
            closeConsumer();
        }
    }

    private void initializeConsumer() {
        log.info("┌─────────────────────────────────────────────────");
        log.info("│ INITIALIZING HUB EVENT CONSUMER");
        log.info("├─────────────────────────────────────────────────");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getConsumer().getHubEvent().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, HubEventDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfig.getConsumer().getHubEvent().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfig.getConsumer().getHubEvent().isEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConfig.getConsumer().getHubEvent().getMaxPollRecords());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        consumer = new KafkaConsumer<>(props);

        log.info("│ ✅ Consumer initialized:");
        log.info("│    bootstrap.servers={}", kafkaConfig.getBootstrapServers());
        log.info("│    group.id={}", kafkaConfig.getConsumer().getHubEvent().getGroupId());
        log.info("│    auto.offset.reset={}", kafkaConfig.getConsumer().getHubEvent().getAutoOffsetReset());
        log.info("│    max.poll.records={}", kafkaConfig.getConsumer().getHubEvent().getMaxPollRecords());
        log.info("└─────────────────────────────────────────────────\n");
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.commitSync();
                consumer.close();
                log.info("✅ HubEventProcessor consumer closed");
            }
        } catch (Exception e) {
            log.error("❌ Error closing HubEventProcessor consumer", e);
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