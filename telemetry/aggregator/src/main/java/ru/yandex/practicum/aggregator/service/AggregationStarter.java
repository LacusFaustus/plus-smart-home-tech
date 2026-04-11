package ru.yandex.practicum.aggregator.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.aggregator.config.KafkaConfig;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final KafkaConsumer<String, SpecificRecordBase> consumer;
    private final Producer<String, SpecificRecordBase> producer;
    private final KafkaConfig kafkaConfig;

    private final Map<String, SensorsSnapshotAvro> snapshots = new HashMap<>();
    private volatile boolean running = true;

    public void start() {
        consumer.subscribe(List.of(kafkaConfig.getTopics().getSensors()));

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running) {
                ConsumerRecords<String, SpecificRecordBase> records =
                        consumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, SpecificRecordBase> record : records) {
                    processRecord(record);
                }

                consumer.commitSync();
            }
        } catch (WakeupException e) {
            log.info("Consumer wakeup called");
        } catch (Exception e) {
            log.error("Ошибка обработки", e);
        } finally {
            consumer.close();
            producer.close();
        }
    }

    private void processRecord(ConsumerRecord<String, SpecificRecordBase> record) {
        SensorEventAvro event = (SensorEventAvro) record.value();
        log.info("Получено событие датчика: id={}, hubId={}, timestamp={}",
                event.getId().toString(),           // ← toString()
                event.getHubId().toString(),        // ← toString()
                event.getTimestamp());

        updateSnapshot(event);
    }

    private void updateSnapshot(SensorEventAvro event) {
        String hubId = event.getHubId().toString();      // ← toString()
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(hubId,
                k -> SensorsSnapshotAvro.newBuilder()
                        .setHubId(hubId)
                        .setTimestamp(Instant.now())
                        .setSensorsState(new HashMap<>())
                        .build());

        String sensorId = event.getId().toString();      // ← toString()
        SensorStateAvro currentState = snapshot.getSensorsState().get(sensorId);

        // Проверяем, нужно ли обновлять состояние
        if (currentState == null ||
                event.getTimestamp().isAfter(currentState.getTimestamp())) {

            SensorStateAvro newState = SensorStateAvro.newBuilder()
                    .setTimestamp(event.getTimestamp())
                    .setData(event.getPayload())
                    .build();

            snapshot.getSensorsState().put(sensorId, newState);
            snapshot.setTimestamp(event.getTimestamp());

            // Отправляем обновленный снапшот в Kafka
            sendSnapshot(snapshot);
            log.info("Снапшот обновлен и отправлен для хаба {}", hubId);
        } else {
            log.debug("Данные датчика {} не изменились, снапшот не обновляется", sensorId);
        }
    }

    private void sendSnapshot(SensorsSnapshotAvro snapshot) {
        ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(
                kafkaConfig.getTopics().getSnapshots(),
                snapshot.getHubId().toString(),  // ← toString()
                snapshot
        );

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Ошибка отправки снапшота", exception);
            } else {
                log.debug("Снапшот отправлен в топик {}, партиция {}",
                        metadata.topic(), metadata.partition());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        consumer.wakeup();
    }
}