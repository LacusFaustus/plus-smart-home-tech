package ru.yandex.practicum.telemetry.analyzer.config;

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

    @Getter
    @Setter
    public static class Topics {
        private String snapshots;
        private String hubs;
    }

    @Getter
    @Setter
    public static class Consumer {
        private SnapshotConsumer snapshot = new SnapshotConsumer();
        private HubEventConsumer hubEvent = new HubEventConsumer();

        @Getter
        @Setter
        public static class SnapshotConsumer {
            private String groupId;
            private String autoOffsetReset;
            private boolean enableAutoCommit;
            private int maxPollRecords;
        }

        @Getter
        @Setter
        public static class HubEventConsumer {
            private String groupId;
            private String autoOffsetReset;
            private boolean enableAutoCommit;
            private int maxPollRecords;
        }
    }
}