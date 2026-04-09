package ru.yandex.practicum.telemetry.analyzer.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.telemetry.analyzer.config.KafkaConfig;
import ru.yandex.practicum.telemetry.analyzer.processor.HubEventProcessor;
import ru.yandex.practicum.telemetry.analyzer.processor.SnapshotProcessor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzerRunner implements CommandLineRunner {

    private final SnapshotProcessor snapshotProcessor;
    private final HubEventProcessor hubEventProcessor;
    private final DataSource dataSource;
    private final KafkaConfig kafkaConfig;

    @Override
    public void run(String... args) throws InterruptedException {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           STARTING ANALYZER                                 ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        // Проверка готовности базы данных
        log.info("Waiting for database to be ready...");
        int retries = 10;
        while (retries > 0) {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    log.info("✅ Database connection successful!");
                    break;
                }
            } catch (Exception e) {
                log.warn("Database not ready yet, waiting... ({} retries left)", retries);
                retries--;
                if (retries == 0) {
                    log.error("Failed to connect to database after 10 attempts", e);
                    throw new RuntimeException("Database connection failed", e);
                }
                Thread.sleep(3000);
            }
        }

        // Проверка готовности Kafka
        log.info("Checking Kafka connection...");
        retries = 10;
        while (retries > 0) {
            try (KafkaConsumer<String, String> testConsumer = new KafkaConsumer<>(getKafkaProperties())) {
                testConsumer.listTopics();
                log.info("✅ Kafka connection successful!");
                break;
            } catch (Exception e) {
                log.warn("Kafka not ready yet, waiting... ({} retries left)", retries);
                retries--;
                if (retries == 0) {
                    log.error("Failed to connect to Kafka after 10 attempts", e);
                    throw new RuntimeException("Kafka connection failed", e);
                }
                Thread.sleep(3000);
            }
        }

        // Увеличенная задержка для полной инициализации
        log.info("Waiting 30 seconds for Kafka and HubRouter initialization...");
        Thread.sleep(30000);

        log.info("Starting Kafka consumers...");
        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        // Даем время на подписку
        Thread.sleep(5000);

        log.info("✅ All processors initialized, starting snapshot processing...");
        snapshotProcessor.start();
    }

    private Properties getKafkaProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        return props;
    }
}