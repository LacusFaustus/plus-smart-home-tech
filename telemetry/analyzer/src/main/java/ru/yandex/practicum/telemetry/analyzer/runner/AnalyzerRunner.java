package ru.yandex.practicum.telemetry.analyzer.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.telemetry.analyzer.processor.HubEventProcessor;
import ru.yandex.practicum.telemetry.analyzer.processor.SnapshotProcessor;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzerRunner implements CommandLineRunner {

    private final SnapshotProcessor snapshotProcessor;
    private final HubEventProcessor hubEventProcessor;

    @Override
    public void run(String... args) {
        log.info("Starting Analyzer...");

        // Запускаем HubEventProcessor в отдельном потоке
        Thread hubEventsThread = new Thread(hubEventProcessor);
        hubEventsThread.setName("HubEventHandlerThread");
        hubEventsThread.start();
        log.info("HubEventProcessor started in separate thread");

        // Запускаем SnapshotProcessor в основном потоке
        snapshotProcessor.start();
        log.info("SnapshotProcessor started in main thread");
    }
}