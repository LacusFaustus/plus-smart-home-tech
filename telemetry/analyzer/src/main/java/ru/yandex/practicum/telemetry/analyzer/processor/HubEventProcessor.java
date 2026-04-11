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

@Component
@Slf4j
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private final KafkaConfig kafkaConfig;
    private final ScenarioService scenarioService;

    private KafkaConsumer<String, HubEventAvro> consumer;
    private volatile boolean running = true;

    @Override
    public void run() {
        log.info("=== HubEventProcessor started ===");
        initConsumer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook received for HubEventProcessor");
            running = false;
            if (consumer != null) {
                consumer.wakeup();
            }
        }));

        try {
            String topic = kafkaConfig.getTopics().getHubs();
            consumer.subscribe(List.of(topic));
            log.info("Subscribed to topic: {}", topic);

            while (running) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(Duration.ofMillis(2000));

                if (records.isEmpty()) {
                    continue;
                }

                log.info("📦 Received {} hub event records", records.count());

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    HubEventAvro event = record.value();
                    if (event == null) {
                        log.warn("Received null hub event at offset: {}", record.offset());
                        continue;
                    }

                    log.info("📨 Processing: offset={}, partition={}, hubId={}",
                            record.offset(), record.partition(), event.getHubId());

                    try {
                        scenarioService.processHubEvent(event);
                        log.info("✅ Successfully processed event for hubId={}", event.getHubId());
                    } catch (Exception e) {
                        log.error("❌ Error processing event for hubId={}, offset={}",
                                event.getHubId(), record.offset(), e);
                        // Не падаем, продолжаем обработку следующего события
                    }
                }

                // Фиксируем offsets после обработки
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
            log.info("Wakeup exception received for HubEventProcessor");
        } catch (Exception e) {
            log.error("Unexpected error in HubEventProcessor", e);
        } finally {
            closeConsumer();
        }
    }

    private void initConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getConsumer().getHubEvent().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, HubEventDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfig.getConsumer().getHubEvent().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfig.getConsumer().getHubEvent().isEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConfig.getConsumer().getHubEvent().getMaxPollRecords());

        consumer = new KafkaConsumer<>(props);
        log.info("HubEventConsumer initialized with group.id: {}",
                kafkaConfig.getConsumer().getHubEvent().getGroupId());
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.commitSync();
                consumer.close();
                log.info("HubEventConsumer closed");
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