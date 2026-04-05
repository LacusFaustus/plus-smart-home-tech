package ru.yandex.practicum.telemetry.aggregator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka")
@Getter
@Setter
public class KafkaConfig {
    private String bootstrapServers;
    private Topics topics = new Topics();
    private Consumer consumer = new Consumer();
    private Producer producer = new Producer();

    @Getter
    @Setter
    public static class Topics {
        private String sensors;
        private String snapshots;
    }

    @Getter
    @Setter
    public static class Consumer {
        private String groupId;
        private String autoOffsetReset;
        private boolean enableAutoCommit;
        private int maxPollRecords;
        private int sessionTimeoutMs;
        private int maxPollIntervalMs;
    }

    @Getter
    @Setter
    public static class Producer {
        private String acks;
        private int retries;
    }
}