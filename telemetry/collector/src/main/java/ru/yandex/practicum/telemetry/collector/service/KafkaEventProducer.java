package ru.yandex.practicum.telemetry.collector.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.model.HubEvent;
import ru.yandex.practicum.telemetry.collector.model.SensorEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

@Service
@Slf4j
public class KafkaEventProducer {

    private final SensorEventConverter sensorEventConverter;
    private final HubEventConverter hubEventConverter;
    private final String sensorsTopic;
    private final String hubsTopic;
    private final Properties kafkaProperties;

    private KafkaProducer<String, byte[]> producer;

    public KafkaEventProducer(
            SensorEventConverter sensorEventConverter,
            HubEventConverter hubEventConverter,
            @Value("${kafka.topics.sensors}") String sensorsTopic,
            @Value("${kafka.topics.hubs}") String hubsTopic,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.sensorEventConverter = sensorEventConverter;
        this.hubEventConverter = hubEventConverter;
        this.sensorsTopic = sensorsTopic;
        this.hubsTopic = hubsTopic;

        this.kafkaProperties = new Properties();
        this.kafkaProperties.put("bootstrap.servers", bootstrapServers);
        this.kafkaProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        this.kafkaProperties.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        this.kafkaProperties.put("acks", "all");
        this.kafkaProperties.put("retries", 3);
        this.kafkaProperties.put("max.in.flight.requests.per.connection", 1);
    }

    @PostConstruct
    public void init() {
        producer = new KafkaProducer<>(kafkaProperties);
        log.info("KafkaProducer initialized");
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.flush();
            producer.close();
            log.info("KafkaProducer closed");
        }
    }

    public void sendSensorEvent(SensorEvent event) {
        try {
            SensorEventAvro avroEvent = sensorEventConverter.toAvro(event);
            byte[] serializedData = serializeSensorEvent(avroEvent);

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    sensorsTopic,
                    event.getId(),
                    serializedData
            );

            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    log.info("Sensor event sent to Kafka: topic={}, partition={}, offset={}",
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset());
                } else {
                    log.error("Failed to send sensor event to Kafka: id={}", event.getId(), exception);
                }
            });
        } catch (Exception e) {
            log.error("Error converting sensor event to Avro: id={}", event.getId(), e);
            throw new RuntimeException("Failed to process sensor event", e);
        }
    }

    public void sendHubEvent(HubEvent event) {
        try {
            HubEventAvro avroEvent = hubEventConverter.toAvro(event);
            byte[] serializedData = serializeHubEvent(avroEvent);

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    hubsTopic,
                    event.getHubId(),
                    serializedData
            );

            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    log.info("Hub event sent to Kafka: topic={}, partition={}, offset={}",
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset());
                } else {
                    log.error("Failed to send hub event to Kafka: hubId={}", event.getHubId(), exception);
                }
            });
        } catch (Exception e) {
            log.error("Error converting hub event to Avro: hubId={}", event.getHubId(), e);
            throw new RuntimeException("Failed to process hub event", e);
        }
    }

    private byte[] serializeSensorEvent(SensorEventAvro event) throws IOException {
        DatumWriter<SensorEventAvro> writer = new SpecificDatumWriter<>(SensorEventAvro.getClassSchema());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(event, encoder);
            encoder.flush();
            return out.toByteArray();
        }
    }

    private byte[] serializeHubEvent(HubEventAvro event) throws IOException {
        DatumWriter<HubEventAvro> writer = new SpecificDatumWriter<>(HubEventAvro.getClassSchema());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(event, encoder);
            encoder.flush();
            return out.toByteArray();
        }
    }
}