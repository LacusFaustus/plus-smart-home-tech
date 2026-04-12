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
                        return;
                    }
                }

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

            try {
                if (scenarioEvaluator.evaluateScenario(scenario, snapshot)) {
                    log.info("Сценарий '{}' активирован для хаба {}", scenario.getName(), hubId);
                    try {
                        executeActions(scenario, snapshot);
                    } catch (Exception e) {
                        log.error("Ошибка при выполнении действий сценария '{}': {}",
                                scenario.getName(), e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при проверке сценария '{}': {}",
                        scenario.getName(), e.getMessage(), e);
            }
        }
    }

    private void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        log.info("Выполнение действий для сценария '{}' хаба '{}'", scenario.getName(), scenario.getHubId());

        for (var entry : scenario.getActions().entrySet()) {
            Sensor sensor = entry.getKey();
            Action action = entry.getValue();

            ActionTypeProto actionType;
            try {
                actionType = ActionTypeProto.valueOf(action.getType());
            } catch (IllegalArgumentException e) {
                log.error("Неизвестный тип действия: {}", action.getType());
                continue;
            }

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

            boolean sent = false;
            int maxRetries = 3;
            int retryCount = 0;
            long retryDelayMs = 1000;

            while (!sent && retryCount < maxRetries) {
                try {
                    log.debug("Попытка {} отправки действия для датчика {}", retryCount + 1, sensor.getId());

                    hubRouterClient.handleDeviceAction(request);

                    log.info("Действие отправлено в hub-router: sensorId={}, type={}, value={}",
                            sensor.getId(), action.getType(), action.getValue());
                    sent = true;

                } catch (io.grpc.StatusRuntimeException e) {
                    io.grpc.Status.Code statusCode = e.getStatus().getCode();
                    String description = e.getStatus().getDescription();

                    log.warn("gRPC ошибка при отправке действия: status={}, description={}, sensorId={}",
                            statusCode, description, sensor.getId());

                    if (statusCode == io.grpc.Status.Code.UNAVAILABLE) {
                        retryCount++;
                        if (retryCount < maxRetries) {
                            log.info("Hub-router недоступен, попытка {}/{} через {} мс",
                                    retryCount + 1, maxRetries, retryDelayMs);
                            try {
                                Thread.sleep(retryDelayMs);
                                retryDelayMs *= 2;
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.error("Ожидание прервано");
                                return;
                            }
                        } else {
                            log.error("Не удалось отправить действие после {} попыток", maxRetries);
                        }
                    } else if (statusCode == io.grpc.Status.Code.UNIMPLEMENTED) {
                        log.info("Метод Hub-router не реализован (тестовый режим). Действие считается отправленным: sensorId={}",
                                sensor.getId());
                        sent = true;
                    } else if (statusCode == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
                        retryCount++;
                        if (retryCount < maxRetries) {
                            log.warn("Таймаут соединения, попытка {}/{}", retryCount + 1, maxRetries);
                            try {
                                Thread.sleep(retryDelayMs);
                                retryDelayMs *= 2;
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.error("Ожидание прервано");
                                return;
                            }
                        } else {
                            log.error("Таймаут соединения после {} попыток", maxRetries);
                        }
                    } else {
                        log.error("Критическая gRPC ошибка: status={}, sensorId={}", statusCode, sensor.getId());
                        break;
                    }
                } catch (Exception e) {
                    log.error("Неожиданная ошибка при отправке действия: sensorId={}, error={}",
                            sensor.getId(), e.getMessage(), e);
                    break;
                }
            }

            if (!sent) {
                log.warn("Действие для датчика {} не отправлено, переходим к следующему", sensor.getId());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        snapshotConsumer.wakeup();
    }
}