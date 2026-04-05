package ru.yandex.practicum.telemetry.collector.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.config.KafkaConfig;
import ru.yandex.practicum.telemetry.collector.model.internal.HubEventInternal;
import ru.yandex.practicum.telemetry.collector.model.internal.SensorEventInternal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final SensorEventConverter sensorEventConverter;
    private final HubEventConverter hubEventConverter;
    private final KafkaConfig kafkaConfig;

    private KafkaProducer<String, byte[]> producer;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        producer = new KafkaProducer<>(props);
        log.info("KafkaProducer initialized for servers: {}", kafkaConfig.getBootstrapServers());
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.flush();
            producer.close();
            log.info("KafkaProducer closed");
        }
    }

    public void sendSensorEvent(SensorEventInternal event) {
        try {
            log.debug("Converting sensor event to Avro: id={}, type={}", event.getId(), event.getType());
            SensorEventAvro avroEvent = sensorEventConverter.toAvro(event);

            log.debug("Serializing Avro event: id={}", event.getId());
            byte[] serializedData = serializeSensorEvent(avroEvent);

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    kafkaConfig.getTopics().getSensors(),
                    event.getId(),
                    serializedData
            );

            log.info("Sending sensor event to Kafka topic: {}, key: {}",
                    kafkaConfig.getTopics().getSensors(), event.getId());

            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    log.info("✅ Sensor event sent successfully: topic={}, partition={}, offset={}, id={}",
                            metadata.topic(), metadata.partition(), metadata.offset(), event.getId());
                } else {
                    log.error("❌ Failed to send sensor event: id={}", event.getId(), exception);
                }
            });

            producer.flush(); // Важно! Принудительная отправка
            log.debug("Flushed producer after sending event: id={}", event.getId());

        } catch (Exception e) {
            log.error("❌ Error sending sensor event: id={}", event.getId(), e);
            throw new RuntimeException("Failed to send sensor event", e);
        }
    }

    public void sendHubEvent(HubEventInternal event) {
        try {
            log.debug("Converting hub event to Avro: hubId={}, type={}", event.getHubId(), event.getType());
            HubEventAvro avroEvent = hubEventConverter.toAvro(event);

            log.debug("Serializing Avro hub event: hubId={}", event.getHubId());
            byte[] serializedData = serializeHubEvent(avroEvent);

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    kafkaConfig.getTopics().getHubs(),
                    event.getHubId(),
                    serializedData
            );

            log.info("Sending hub event to Kafka topic: {}, key: {}",
                    kafkaConfig.getTopics().getHubs(), event.getHubId());

            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    log.info("✅ Hub event sent successfully: topic={}, partition={}, offset={}, hubId={}",
                            metadata.topic(), metadata.partition(), metadata.offset(), event.getHubId());
                } else {
                    log.error("❌ Failed to send hub event: hubId={}", event.getHubId(), exception);
                }
            });

            producer.flush();
            log.debug("Flushed producer after sending hub event: hubId={}", event.getHubId());

        } catch (Exception e) {
            log.error("❌ Error sending hub event: hubId={}", event.getHubId(), e);
            throw new RuntimeException("Failed to send hub event", e);
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