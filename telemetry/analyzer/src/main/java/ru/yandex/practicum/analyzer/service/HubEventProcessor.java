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
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

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

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    processHubEvent(record.value());
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
        sensorRepository.deleteById(event.getId().toString());
        log.info("Удалён датчик: id={}, hubId={}", event.getId(), hubId);
    }

    private void handleScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName().toString();

        // Удаляем старый сценарий, если существует
        scenarioRepository.findByHubIdAndName(hubId, scenarioName)
                .ifPresent(scenario -> {
                    scenarioRepository.delete(scenario);
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
            Sensor sensor = sensorRepository.findById(sensorId)
                    .orElseGet(() -> sensorRepository.save(
                            Sensor.builder().id(sensorId).hubId(hubId).build()
                    ));

            Condition condition = AvroToEntityMapper.mapToCondition(conditionAvro);
            condition = conditionRepository.save(condition);

            scenario.getConditions().put(sensor, condition);
        }

        // Добавляем действия
        for (DeviceActionAvro actionAvro : event.getActions()) {
            String sensorId = actionAvro.getSensorId().toString();
            Sensor sensor = sensorRepository.findById(sensorId)
                    .orElseGet(() -> sensorRepository.save(
                            Sensor.builder().id(sensorId).hubId(hubId).build()
                    ));

            Action action = AvroToEntityMapper.mapToAction(actionAvro);
            action = actionRepository.save(action);

            scenario.getActions().put(sensor, action);
        }

        scenarioRepository.save(scenario);
        log.info("Добавлен сценарий: hubId={}, name={}, conditions={}, actions={}",
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