package ru.yandex.practicum.telemetry.aggregator.processor;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.aggregator.config.KafkaConfig;
import ru.yandex.practicum.telemetry.aggregator.deserializer.SensorEventDeserializer;
import ru.yandex.practicum.telemetry.aggregator.service.AggregationService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Component
@Slf4j
@RequiredArgsConstructor
public class AggregationProcessor {

    private final KafkaConfig kafkaConfig;
    private final AggregationService aggregationService;

    private KafkaConsumer<String, SensorEventAvro> consumer;
    private KafkaProducer<String, byte[]> producer;

    private volatile boolean running = true;

    public void start() {
        initializeConsumer();
        initializeProducer();

        // Регистрируем хук для корректного завершения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook received, stopping AggregationProcessor...");
            running = false;
            if (consumer != null) {
                consumer.wakeup();
            }
        }));

        try {
            consumer.subscribe(List.of(kafkaConfig.getTopics().getSensors()));
            log.info("AggregationProcessor subscribed to topic: {}", kafkaConfig.getTopics().getSensors());

            while (running) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(Duration.ofMillis(1000));

                if (records.isEmpty()) {
                    continue;
                }

                log.debug("Received {} records from Kafka", records.count());

                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    SensorEventAvro event = record.value();
                    if (event == null) {
                        log.warn("Received null event at offset: {}", record.offset());
                        continue;
                    }

                    try {
                        Optional<SensorsSnapshotAvro> updatedSnapshot = aggregationService.updateSnapshot(event);

                        if (updatedSnapshot.isPresent()) {
                            sendSnapshot(updatedSnapshot.get());
                            log.info("Snapshot sent to Kafka for hub: {}", updatedSnapshot.get().getHubId());
                        }
                    } catch (Exception e) {
                        log.error("Error processing event: id={}, hubId={}", event.getId(), event.getHubId(), e);
                    }
                }

                // Фиксируем смещения после обработки всей пачки
                try {
                    consumer.commitSync();
                    log.debug("Committed offsets for {} records", records.count());
                } catch (CommitFailedException e) {
                    log.error("Commit failed", e);
                }
            }

        } catch (WakeupException e) {
            log.info("Wakeup exception received, shutting down...");
        } catch (Exception e) {
            log.error("Unexpected error in AggregationProcessor", e);
        } finally {
            closeResources();
        }
    }

    private void initializeConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getConsumer().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorEventDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfig.getConsumer().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfig.getConsumer().isEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConfig.getConsumer().getMaxPollRecords());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafkaConfig.getConsumer().getSessionTimeoutMs());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaConfig.getConsumer().getMaxPollIntervalMs());

        consumer = new KafkaConsumer<>(props);
        log.info("Kafka consumer initialized with group.id: {}", kafkaConfig.getConsumer().getGroupId());
    }

    private void initializeProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put(ProducerConfig.ACKS_CONFIG, kafkaConfig.getProducer().getAcks());
        props.put(ProducerConfig.RETRIES_CONFIG, kafkaConfig.getProducer().getRetries());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        producer = new KafkaProducer<>(props);
        log.info("Kafka producer initialized");
    }

    private void sendSnapshot(SensorsSnapshotAvro snapshot) {
        try {
            byte[] serializedData = serializeSnapshot(snapshot);

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    kafkaConfig.getTopics().getSnapshots(),
                    snapshot.getHubId(),
                    serializedData
            );

            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    log.debug("Snapshot sent: hubId={}, partition={}, offset={}",
                            snapshot.getHubId(), metadata.partition(), metadata.offset());
                } else {
                    log.error("Failed to send snapshot for hub: {}", snapshot.getHubId(), exception);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing snapshot for hub: {}", snapshot.getHubId(), e);
        }
    }

    private byte[] serializeSnapshot(SensorsSnapshotAvro snapshot) throws IOException {
        DatumWriter<SensorsSnapshotAvro> writer = new SpecificDatumWriter<>(SensorsSnapshotAvro.getClassSchema());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(snapshot, encoder);
            encoder.flush();
            return out.toByteArray();
        }
    }

    private void closeResources() {
        log.info("Closing resources...");

        if (producer != null) {
            try {
                producer.flush();
                producer.close();
                log.info("Producer closed");
            } catch (Exception e) {
                log.error("Error closing producer", e);
            }
        }

        if (consumer != null) {
            try {
                consumer.commitSync();
                consumer.close();
                log.info("Consumer closed");
            } catch (Exception e) {
                log.error("Error closing consumer", e);
            }
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