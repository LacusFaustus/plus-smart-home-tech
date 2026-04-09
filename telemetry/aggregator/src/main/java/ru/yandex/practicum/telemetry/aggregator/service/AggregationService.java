package ru.yandex.practicum.telemetry.aggregator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AggregationService {

    private final Map<String, SensorsSnapshotAvro> snapshots = new ConcurrentHashMap<>();

    public Optional<SensorsSnapshotAvro> updateSnapshot(SensorEventAvro event) {
        String hubId = event.getHubId();
        String sensorId = event.getId();

        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                         AGGREGATOR: PROCESSING SENSOR EVENT               ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
        log.info("║ 📥 INPUT:                                                                  ║");
        log.info("║    hubId={}", hubId);
        log.info("║    sensorId={}", sensorId);
        log.info("║    timestamp={}", event.getTimestamp());
        log.info("║    payloadType={}", event.getPayload().getClass().getSimpleName());
        log.info("║    payloadValue={}", event.getPayload());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");

        // Получаем или создаем снапшот
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(hubId, id -> {
            log.info("┌─────────────────────────────────────────────────────────────────────────┐");
            log.info("│ DECISION: Creating new snapshot for hubId={}", hubId);
            log.info("│ REASON: First event received from this hub                              │");
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
            return SensorsSnapshotAvro.newBuilder()
                    .setHubId(hubId)
                    .setTimestamp(event.getTimestamp())
                    .setSensorsState(new ConcurrentHashMap<>())
                    .build();
        });

        log.info("📸 Current snapshot state:");
        log.info("    hubId={}", snapshot.getHubId());
        log.info("    snapshotTimestamp={}", snapshot.getTimestamp());
        log.info("    sensorsCount={}", snapshot.getSensorsState().size());

        // Проверяем существующее состояние
        SensorStateAvro existingState = snapshot.getSensorsState().get(sensorId);

        if (existingState != null) {
            log.info("📊 Existing sensor state:");
            log.info("    timestamp={}", existingState.getTimestamp());
            log.info("    data={}", existingState.getData());
        } else {
            log.info("📊 Existing sensor state: none (first event for this sensor)");
        }

        // ПРОВЕРКА 1: устаревшее событие
        if (existingState != null && existingState.getTimestamp() > event.getTimestamp()) {
            log.info("┌─────────────────────────────────────────────────────────────────────────┐");
            log.info("│ DECISION: Ignoring outdated event                                      │");
            log.info("│ REASON: Event timestamp {} is OLDER than current timestamp {}",
                    event.getTimestamp(), existingState.getTimestamp());
            log.info("│ ACTION: Snapshot will NOT be updated                                   │");
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
            log.info("╔════════════════════════════════════════════════════════════════════════════╗");
            log.info("║ OUTPUT: Optional.empty() - no snapshot update                              ║");
            log.info("╚════════════════════════════════════════════════════════════════════════════╝\n");
            return Optional.empty();
        }

        // ПРОВЕРКА 2: данные не изменились
        if (existingState != null && existingState.getData().equals(event.getPayload())) {
            log.info("┌─────────────────────────────────────────────────────────────────────────┐");
            log.info("│ DECISION: Skipping snapshot update                                     │");
            log.info("│ REASON: Sensor data unchanged                                          │");
            log.info("│    oldValue={}", existingState.getData());
            log.info("│    newValue={}", event.getPayload());
            log.info("│ ACTION: Snapshot will NOT be updated                                   │");
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
            log.info("╔════════════════════════════════════════════════════════════════════════════╗");
            log.info("║ OUTPUT: Optional.empty() - no snapshot update                              ║");
            log.info("╚════════════════════════════════════════════════════════════════════════════╝\n");
            return Optional.empty();
        }

        // ПРИНЯТИЕ РЕШЕНИЯ: обновляем снапшот
        if (existingState == null) {
            log.info("┌─────────────────────────────────────────────────────────────────────────┐");
            log.info("│ DECISION: Adding new sensor to snapshot                                │");
            log.info("│ REASON: First event received for sensorId={}", sensorId);
            log.info("│ ACTION: Creating new SensorState and adding to snapshot                │");
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
        } else {
            log.info("┌─────────────────────────────────────────────────────────────────────────┐");
            log.info("│ DECISION: Updating existing sensor data                                │");
            log.info("│ REASON: Sensor data CHANGED                                            │");
            log.info("│    oldValue={}", existingState.getData());
            log.info("│    newValue={}", event.getPayload());
            log.info("│ ACTION: Replacing SensorState in snapshot                              │");
            log.info("└─────────────────────────────────────────────────────────────────────────┘");
        }

        // Создаем новое состояние
        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(event.getPayload())
                .build();

        // Обновляем снапшот
        snapshot.getSensorsState().put(sensorId, newState);
        snapshot.setTimestamp(event.getTimestamp());

        log.info("┌─────────────────────────────────────────────────────────────────────────┐");
        log.info("│ ✅ SNAPSHOT UPDATED SUCCESSFULLY                                        │");
        log.info("├─────────────────────────────────────────────────────────────────────────┤");
        log.info("│ 📤 OUTPUT:                                                               │");
        log.info("│    hubId={}", hubId);
        log.info("│    snapshotTimestamp={}", snapshot.getTimestamp());
        log.info("│    totalSensors={}", snapshot.getSensorsState().size());
        log.info("│    updatedSensorId={}", sensorId);
        log.info("│    newSensorData={}", newState.getData());
        log.info("└─────────────────────────────────────────────────────────────────────────┘");
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║ OUTPUT: Optional.of(snapshot) - snapshot will be sent to Kafka             ║");
        log.info("╚════════════════════════════════════════════════════════════════════════════╝\n");

        return Optional.of(snapshot);
    }

    public Map<String, SensorsSnapshotAvro> getSnapshots() {
        return new ConcurrentHashMap<>(snapshots);
    }
}