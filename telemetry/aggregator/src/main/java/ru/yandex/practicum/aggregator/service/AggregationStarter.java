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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final KafkaConsumer<String, SpecificRecordBase> consumer;
    private final Producer<String, SpecificRecordBase> producer;
    private final KafkaConfig kafkaConfig;

    private final Map<String, SensorsSnapshotAvro> snapshots = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public void start() {
        consumer.subscribe(List.of(kafkaConfig.getTopics().getSensors()));

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running) {
                ConsumerRecords<String, SpecificRecordBase> records =
                        consumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                for (ConsumerRecord<String, SpecificRecordBase> record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        log.error("Ошибка обработки записи: {}", e.getMessage(), e);
                        return;
                    }
                }

                consumer.commitSync();
            }
        } catch (WakeupException e) {
            log.info("Consumer wakeup called");
        } catch (Exception e) {
            log.error("Ошибка обработки", e);
        } finally {
            try {
                consumer.commitSync();
            } catch (Exception e) {
                log.error("Ошибка при финальном коммите", e);
            }
            consumer.close();
            producer.close();
            log.info("Консьюмер и продюсер закрыты");
        }
    }

    private void processRecord(ConsumerRecord<String, SpecificRecordBase> record) {
        SensorEventAvro event = (SensorEventAvro) record.value();
        log.info("Получено событие датчика: id={}, hubId={}, timestamp={}",
                event.getId().toString(),
                event.getHubId().toString(),
                event.getTimestamp());

        updateSnapshot(event).ifPresent(this::sendSnapshot);
    }

    private Optional<SensorsSnapshotAvro> updateSnapshot(SensorEventAvro event) {
        String hubId = event.getHubId().toString();
        String sensorId = event.getId().toString();

        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(event.getPayload())
                .build();

        SensorsSnapshotAvro snapshotToSend;

        synchronized (snapshots) {
            SensorsSnapshotAvro existingSnapshot = snapshots.get(hubId);

            if (existingSnapshot == null) {
                // Используем HashMap с String ключами
                Map<CharSequence, SensorStateAvro> states = new HashMap<>();
                states.put(sensorId, newState);

                snapshotToSend = SensorsSnapshotAvro.newBuilder()
                        .setHubId(hubId)
                        .setTimestamp(event.getTimestamp())
                        .setSensorsState(states)
                        .build();

                snapshots.put(hubId, snapshotToSend);
                log.info("Создан новый снапшот для хаба {} с датчиком {}", hubId, sensorId);

                return Optional.of(snapshotToSend);
            }

            SensorStateAvro oldState = existingSnapshot.getSensorsState().get(sensorId);

            Object oldData = oldState != null ? oldState.getData() : null;
            Object newData = newState.getData();

            if (oldState != null && Objects.equals(oldData, newData)) {
                log.debug("Данные датчика {} не изменились, снапшот НЕ отправляется", sensorId);
                return Optional.empty();
            }

            // Создаем новую мапу с String ключами
            Map<CharSequence, SensorStateAvro> updatedStates = new HashMap<>();
            for (Map.Entry<CharSequence, SensorStateAvro> entry : existingSnapshot.getSensorsState().entrySet()) {
                updatedStates.put(entry.getKey().toString(), entry.getValue());
            }
            updatedStates.put(sensorId, newState);

            snapshotToSend = SensorsSnapshotAvro.newBuilder()
                    .setHubId(hubId)
                    .setTimestamp(event.getTimestamp())
                    .setSensorsState(updatedStates)
                    .build();

            snapshots.put(hubId, snapshotToSend);

            if (oldState == null) {
                log.info("Добавлен новый датчик {} в снапшот хаба {}", sensorId, hubId);
            } else {
                log.info("Обновлены данные датчика {} в снапшоте хаба {}", sensorId, hubId);
            }

            return Optional.of(snapshotToSend);
        }
    }

    private void sendSnapshot(SensorsSnapshotAvro snapshot) {
        String snapshotTopic = kafkaConfig.getTopics().getSnapshots();
        log.debug("Отправка снапшота для хаба {} в топик {}", snapshot.getHubId(), snapshotTopic);

        try {
            ProducerRecord<String, SpecificRecordBase> record =
                    new ProducerRecord<>(snapshotTopic, snapshot.getHubId().toString(), snapshot);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Ошибка отправки снапшота в Kafka: {}", exception.getMessage(), exception);
                } else {
                    log.debug("Снапшот отправлен в топик {}, партиция {}, оффсет {}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
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