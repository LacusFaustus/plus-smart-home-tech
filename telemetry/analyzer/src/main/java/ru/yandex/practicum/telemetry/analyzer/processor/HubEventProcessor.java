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
@RequiredArgsConstructor
@Slf4j
public class HubEventProcessor implements Runnable {

    private final KafkaConfig kafkaConfig;
    private final ScenarioService scenarioService;

    private KafkaConsumer<String, HubEventAvro> consumer;
    private volatile boolean running = true;

    @Override
    public void run() {
        initializeConsumer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook received for HubEventProcessor");
            running = false;
            if (consumer != null) {
                consumer.wakeup();
            }
        }));

        try {
            consumer.subscribe(List.of(kafkaConfig.getTopics().getHubs()));
            log.info("HubEventProcessor subscribed to topic: {}", kafkaConfig.getTopics().getHubs());

            while (running) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    HubEventAvro event = record.value();
                    if (event == null) {
                        log.warn("Received null hub event at offset: {}", record.offset());
                        continue;
                    }

                    log.debug("Processing hub event: hubId={}, offset={}",
                            event.getHubId(), record.offset());

                    try {
                        scenarioService.processHubEvent(event);
                    } catch (Exception e) {
                        log.error("Error processing hub event for hubId: {}", event.getHubId(), e);
                    }
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                    log.debug("Committed offsets for {} hub event records", records.count());
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

    private void initializeConsumer() {
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
        log.info("HubEventProcessor consumer initialized with group.id: {}",
                kafkaConfig.getConsumer().getHubEvent().getGroupId());
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.commitSync();
                consumer.close();
                log.info("HubEventProcessor consumer closed");
            }
        } catch (Exception e) {
            log.error("Error closing HubEventProcessor consumer", e);
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