package ru.yandex.practicum.analyzer.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.config.KafkaConfig;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.analyzer.mapper.AvroToEntityMapper;
import ru.yandex.practicum.analyzer.repository.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {
    private final KafkaConsumer<String, HubEventAvro> hubEventConsumer;
    private final KafkaConfig kafkaConfig;
    private final SensorRepository sensorRepository;
    private final ScenarioRepository scenarioRepository;

    private volatile boolean running = true;

    @Override
    public void run() {
        hubEventConsumer.subscribe(List.of(kafkaConfig.getTopics().getHubEvents()));
        log.info("HubEventProcessor подписан на топик: {}", kafkaConfig.getTopics().getHubEvents());

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        try {
            while (running) {
                ConsumerRecords<String, HubEventAvro> records =
                        hubEventConsumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) {
                    continue;
                }

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    try {
                        processHubEvent(record.value());
                    } catch (Exception e) {
                        log.error("Ошибка обработки события хаба: {}", e.getMessage(), e);
                        // Не коммитим, если не обработали
                        return;
                    }
                }

                hubEventConsumer.commitSync();
            }
        } catch (Exception e) {
            log.error("Ошибка в HubEventProcessor", e);
        } finally {
            try {
                hubEventConsumer.commitSync();
            } catch (Exception e) {
                log.error("Ошибка при финальном коммите", e);
            }
            hubEventConsumer.close();
            log.info("HubEventConsumer закрыт");
        }
    }

    @Transactional
    private void processHubEvent(HubEventAvro event) {
        String hubId = event.getHubId().toString();
        log.info("Получено событие хаба: hubId={}, type={}",
                hubId, event.getPayload().getClass().getSimpleName());

        Object payload = event.getPayload();

        if (payload instanceof DeviceAddedEventAvro) {
            handleDeviceAdded(hubId, (DeviceAddedEventAvro) payload);
        } else if (payload instanceof DeviceRemovedEventAvro) {
            handleDeviceRemoved(hubId, (DeviceRemovedEventAvro) payload);
        } else if (payload instanceof ScenarioAddedEventAvro) {
            handleScenarioAdded(hubId, (ScenarioAddedEventAvro) payload);
        } else if (payload instanceof ScenarioRemovedEventAvro) {
            handleScenarioRemoved(hubId, (ScenarioRemovedEventAvro) payload);
        }
    }

    private void handleDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        Sensor sensor = AvroToEntityMapper.mapToSensor(event, hubId);
        sensorRepository.save(sensor);
        log.info("Добавлен датчик: id={}, hubId={}", sensor.getId(), hubId);
    }

    private void handleDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        String sensorId = event.getId().toString();
        sensorRepository.deleteByHubIdAndId(hubId, sensorId);
        log.info("Удалён датчик: id={}, hubId={}", sensorId, hubId);
    }

    @Transactional
    private void handleScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName().toString();
        log.info("Добавление/обновление сценария: hubId={}, name={}", hubId, scenarioName);

        // Удаляем старый сценарий, если существует
        scenarioRepository.findByHubIdAndName(hubId, scenarioName)
                .ifPresent(existingScenario -> {
                    scenarioRepository.delete(existingScenario);
                    log.info("Удалён существующий сценарий: hubId={}, name={}", hubId, scenarioName);
                });

        // Создаём новый сценарий
        Scenario scenario = Scenario.builder()
                .hubId(hubId)
                .name(scenarioName)
                .conditions(new HashMap<>())
                .actions(new HashMap<>())
                .build();

        // Добавляем условия
        for (ScenarioConditionAvro conditionAvro : event.getConditions()) {
            String sensorId = conditionAvro.getSensorId().toString();

            Sensor sensor = sensorRepository.findByIdAndHubId(sensorId, hubId)
                    .orElseGet(() -> {
                        log.info("Датчик с id={} не найден, создаем новый", sensorId);
                        return sensorRepository.save(Sensor.builder().id(sensorId).hubId(hubId).build());
                    });

            Condition condition = AvroToEntityMapper.mapToCondition(conditionAvro);
            scenario.getConditions().put(sensor, condition);
        }

        // Добавляем действия
        for (DeviceActionAvro actionAvro : event.getActions()) {
            String sensorId = actionAvro.getSensorId().toString();

            Sensor sensor = sensorRepository.findByIdAndHubId(sensorId, hubId)
                    .orElseGet(() -> {
                        log.info("Датчик с id={} не найден, создаем новый", sensorId);
                        return sensorRepository.save(Sensor.builder().id(sensorId).hubId(hubId).build());
                    });

            Action action = AvroToEntityMapper.mapToAction(actionAvro);
            scenario.getActions().put(sensor, action);
        }

        scenarioRepository.save(scenario);
        log.info("Сценарий сохранен: hubId={}, name={}, conditions={}, actions={}",
                hubId, scenarioName, scenario.getConditions().size(), scenario.getActions().size());
    }

    private void handleScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        String scenarioName = event.getName().toString();
        scenarioRepository.deleteByHubIdAndName(hubId, scenarioName);
        log.info("Удалён сценарий: hubId={}, name={}", hubId, scenarioName);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        hubEventConsumer.wakeup();
    }
}