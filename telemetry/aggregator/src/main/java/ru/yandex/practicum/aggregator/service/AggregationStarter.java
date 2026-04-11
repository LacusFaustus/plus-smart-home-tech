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
                event.getId().toString(),
                event.getHubId().toString(),
                event.getTimestamp());

        updateSnapshot(event);
    }

    private void updateSnapshot(SensorEventAvro event) {
        String hubId = event.getHubId().toString();
        String sensorId = event.getId().toString();

        // 1. Создаем новое состояние датчика из события
        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(event.getPayload())
                .build();

        // 2. Атомарно обновляем мапу снапшотов
        SensorsSnapshotAvro snapshotToSend;

        synchronized (snapshots) {
            SensorsSnapshotAvro existingSnapshot = snapshots.get(hubId);

            if (existingSnapshot == null) {
                // Создаем новый снапшот
                Map<CharSequence, SensorStateAvro> states = new HashMap<>();
                states.put(sensorId, newState);
                snapshotToSend = SensorsSnapshotAvro.newBuilder()
                        .setHubId(hubId)
                        .setTimestamp(event.getTimestamp())
                        .setSensorsState(states)
                        .build();
                log.info("Создан новый снапшот для хаба {}", hubId);
            } else {
                // Создаем копию существующей мапы
                Map<CharSequence, SensorStateAvro> updatedStates =
                        new HashMap<>(existingSnapshot.getSensorsState());
                updatedStates.put(sensorId, newState);

                snapshotToSend = SensorsSnapshotAvro.newBuilder()
                        .setHubId(hubId)  // Важно: явно устанавливаем hubId как String
                        .setTimestamp(event.getTimestamp())
                        .setSensorsState(updatedStates)
                        .build();

                SensorStateAvro oldState = existingSnapshot.getSensorsState().get(sensorId);
                if (oldState == null || !oldState.equals(newState)) {
                    log.info("Снапшот обновлен для хаба {}", hubId);
                } else {
                    log.debug("Данные датчика {} не изменились, но снапшот отправлен", sensorId);
                }
            }

            // Сохраняем обновленный снапшот
            snapshots.put(hubId, snapshotToSend);
        }

        // 3. Отправляем снапшот
        sendSnapshot(snapshotToSend);
    }

    private void sendSnapshot(SensorsSnapshotAvro snapshot) {
        String snapshotTopic = kafkaConfig.getTopics().getSnapshots();
        log.debug("Отправка снапшота для хаба {} в топик {}", snapshot.getHubId(), snapshotTopic);
        try {
            ProducerRecord<String, SpecificRecordBase> record =
                    new ProducerRecord<>(snapshotTopic, snapshot);
            producer.send(record);
        } catch (Exception e) {
            log.error("Ошибка отправки снапшота: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        consumer.wakeup();
    }
}