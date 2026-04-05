package ru.yandex.practicum.telemetry.aggregator.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.telemetry.aggregator.processor.AggregationProcessor;

@Component
@RequiredArgsConstructor
@Slf4j
public class AggregationRunner implements CommandLineRunner {

    private final AggregationProcessor aggregationProcessor;

    @Override
    public void run(String... args) {
        log.info("Starting Aggregation Processor...");
        aggregationProcessor.start();
    }
}