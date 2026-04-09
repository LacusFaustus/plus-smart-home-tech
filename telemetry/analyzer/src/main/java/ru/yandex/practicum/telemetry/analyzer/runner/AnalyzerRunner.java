package ru.yandex.practicum.telemetry.analyzer.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.telemetry.analyzer.processor.HubEventProcessor;
import ru.yandex.practicum.telemetry.analyzer.processor.SnapshotProcessor;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzerRunner implements CommandLineRunner {

    private final SnapshotProcessor snapshotProcessor;
    private final HubEventProcessor hubEventProcessor;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws InterruptedException {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           STARTING ANALYZER                                 ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

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

        // Увеличенная задержка для полной инициализации
        log.info("Waiting 30 seconds for Kafka and HubRouter initialization...");
        Thread.sleep(30000);  // ← 30 секунд вместо 15

        log.info("Starting Kafka consumers...");
        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        Thread.sleep(5000);

        log.info("✅ All processors initialized, starting snapshot processing...");
        snapshotProcessor.start();
    }
}