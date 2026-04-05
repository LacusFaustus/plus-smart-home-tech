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

    /**
     * Обновляет снапшот на основе полученного события от датчика.
     *
     * @param event событие от датчика
     * @return Optional с обновлённым снапшотом, если данные изменились, иначе Optional.empty()
     */
    public Optional<SensorsSnapshotAvro> updateSnapshot(SensorEventAvro event) {
        String hubId = event.getHubId();
        String sensorId = event.getId();

        log.info("Processing sensor event: hubId={}, sensorId={}, type={}, timestamp={}",
                hubId, sensorId, event.getPayload().getClass().getSimpleName(), event.getTimestamp());

        // Получаем или создаём снапшот для хаба
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(hubId, id -> {
            log.info("Creating new snapshot for hub: {}", hubId);
            return SensorsSnapshotAvro.newBuilder()
                    .setHubId(hubId)
                    .setTimestamp(event.getTimestamp())
                    .setSensorsState(new ConcurrentHashMap<>())
                    .build();
        });

        // Проверяем существующее состояние датчика
        SensorStateAvro existingState = snapshot.getSensorsState().get(sensorId);

        // Если событие устаревшее - игнорируем
        if (existingState != null && existingState.getTimestamp() > event.getTimestamp()) {
            log.debug("Ignoring outdated event for sensor: {} (event timestamp: {}, current timestamp: {})",
                    sensorId, event.getTimestamp(), existingState.getTimestamp());
            return Optional.empty();
        }

        // Проверяем, изменились ли данные
        if (existingState != null && existingState.getData().equals(event.getPayload())) {
            log.info("Data for sensor {} hasn't changed (same value), skipping snapshot update", sensorId);
            return Optional.empty();
        }

        // Создаём новое состояние датчика
        SensorStateAvro newState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(event.getPayload())
                .build();

        // Логируем изменение
        if (existingState == null) {
            log.info("New sensor detected: hubId={}, sensorId={}, value={}",
                    hubId, sensorId, getSensorValueString(event));
        } else {
            log.info("Sensor data changed: hubId={}, sensorId={}, oldValue={}, newValue={}",
                    hubId, sensorId, getSensorValueString(existingState.getData()), getSensorValueString(event));
        }

        // Обновляем снапшот
        snapshot.getSensorsState().put(sensorId, newState);
        snapshot.setTimestamp(event.getTimestamp());

        log.info("Snapshot updated for hub: {}, sensors count: {}", hubId, snapshot.getSensorsState().size());

        return Optional.of(snapshot);
    }

    private String getSensorValueString(SensorEventAvro event) {
        return event.getPayload().toString();
    }

    private String getSensorValueString(Object data) {
        return data.toString();
    }

    public Map<String, SensorsSnapshotAvro> getSnapshots() {
        return new ConcurrentHashMap<>(snapshots);
    }
}