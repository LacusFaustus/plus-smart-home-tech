package ru.yandex.practicum.telemetry.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.model.HubEvent;
import ru.yandex.practicum.telemetry.collector.model.SensorEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventProducer {

    private final KafkaTemplate<String, byte[]> sensorEventKafkaTemplate;
    private final KafkaTemplate<String, byte[]> hubEventKafkaTemplate;
    private final SensorEventConverter sensorEventConverter;
    private final HubEventConverter hubEventConverter;

    @Value("${kafka.topics.sensors}")
    private String sensorsTopic;

    @Value("${kafka.topics.hubs}")
    private String hubsTopic;

    public void sendSensorEvent(SensorEvent event) {
        try {
            SensorEventAvro avroEvent = sensorEventConverter.toAvro(event);
            byte[] serializedData = serializeSensorEvent(avroEvent);

            sensorEventKafkaTemplate.send(sensorsTopic, event.getId(), serializedData)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Sensor event sent to Kafka: topic={}, partition={}, offset={}",
                                    sensorsTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to send sensor event to Kafka: id={}", event.getId(), ex);
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

            hubEventKafkaTemplate.send(hubsTopic, event.getHubId(), serializedData)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Hub event sent to Kafka: topic={}, partition={}, offset={}",
                                    hubsTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to send hub event to Kafka: hubId={}", event.getHubId(), ex);
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