package ru.yandex.practicum.telemetry.analyzer.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.telemetry.analyzer.processor.HubEventProcessor;
import ru.yandex.practicum.telemetry.analyzer.processor.SnapshotProcessor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzerRunner implements CommandLineRunner {

    private final SnapshotProcessor snapshotProcessor;
    private final HubEventProcessor hubEventProcessor;

    @Override
    public void run(String... args) throws InterruptedException {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           STARTING ANALYZER                                 ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        // Увеличенная задержка для полной инициализации Spring контекста
        Thread.sleep(10000);

        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();

        // Дополнительная задержка для подписки на Kafka
        Thread.sleep(5000);

        log.info("✅ All processors initialized, starting snapshot processing...");
        snapshotProcessor.start();
    }
}