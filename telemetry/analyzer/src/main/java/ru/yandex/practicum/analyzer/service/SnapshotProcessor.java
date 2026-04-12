package ru.yandex.practicum.analyzer.service;

import com.google.protobuf.Timestamp;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.config.KafkaConfig;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotProcessor {
    private final KafkaConsumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final KafkaConfig kafkaConfig;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioEvaluator scenarioEvaluator;

    @GrpcClient("hub-router")
    private HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    private volatile boolean running = true;

    public void start() {
        snapshotConsumer.subscribe(List.of(kafkaConfig.getTopics().getSnapshots()));
        log.info("SnapshotProcessor подписан на топик: {}", kafkaConfig.getTopics().getSnapshots());

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running) {
                ConsumerRecords<String, SensorsSnapshotAvro> records =
                        snapshotConsumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    try {
                        processSnapshot(record.value());
                    } catch (Exception e) {
                        log.error("Ошибка обработки снапшота: {}", e.getMessage(), e);
                        // Не коммитим, если не обработали
                        return;
                    }
                }

                // Коммитим только если все снапшоты успешно обработаны
                snapshotConsumer.commitSync();
            }
        } catch (Exception e) {
            log.error("Ошибка в SnapshotProcessor", e);
        } finally {
            try {
                snapshotConsumer.commitSync();
            } catch (Exception e) {
                log.error("Ошибка при финальном коммите", e);
            }
            snapshotConsumer.close();
            log.info("SnapshotConsumer закрыт");
        }
    }

    private void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId().toString();
        Map<CharSequence, ?> sensorsState = snapshot.getSensorsState();

        log.info("Получен снапшот для хаба: hubId={}, датчиков: {}",
                hubId, sensorsState != null ? sensorsState.size() : 0);

        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);

        if (scenarios.isEmpty()) {
            log.debug("Нет сценариев для хаба: {}", hubId);
            return;
        }

        for (Scenario scenario : scenarios) {
            log.debug("Проверка сценария: {}", scenario.getName());

            if (scenarioEvaluator.evaluateScenario(scenario, snapshot)) {
                log.info("Сценарий '{}' активирован для хаба {}", scenario.getName(), hubId);
                executeActions(scenario, snapshot);
            }
        }
    }

    private void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("Выполнение действий для сценария '{}' хаба '{}'", scenario.getName(), scenario.getHubId());

        for (var entry : scenario.getActions().entrySet()) {
            Sensor sensor = entry.getKey();
            Action action = entry.getValue();

            try {
                ActionTypeProto actionType = ActionTypeProto.valueOf(action.getType());

                DeviceActionRequest request = DeviceActionRequest.newBuilder()
                        .setHubId(scenario.getHubId())
                        .setScenarioName(scenario.getName())
                        .setAction(DeviceActionProto.newBuilder()
                                .setSensorId(sensor.getId())
                                .setType(actionType)
                                .setValue(action.getValue() != null ? action.getValue() : 0)
                                .build())
                        .setTimestamp(Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                                .setNanos(Instant.now().getNano())
                                .build())
                        .build();

                log.debug("Отправка запроса в hub-router: {}", request);

                // Вызываем реальный метод
                hubRouterClient.handleDeviceAction(request);

                log.info("Действие отправлено в hub-router: sensorId={}, type={}, value={}",
                        sensor.getId(), action.getType(), action.getValue());

            } catch (io.grpc.StatusRuntimeException e) {
                log.error("Ошибка gRPC при отправке действия в hub-router: status={}, description={}, sensorId={}",
                        e.getStatus().getCode(), e.getStatus().getDescription(), sensor.getId(), e);
                throw new RuntimeException("Ошибка отправки действия в hub-router", e);
            } catch (Exception e) {
                log.error("Неожиданная ошибка при отправке действия в hub-router: sensorId={}, error={}",
                        sensor.getId(), e.getMessage(), e);
                throw new RuntimeException("Неожиданная ошибка при отправке действия", e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        snapshotConsumer.wakeup();
    }
}