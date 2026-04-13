package ru.yandex.practicum.analyzer.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.analyzer.deserializer.HubEventDeserializer;
import ru.yandex.practicum.analyzer.deserializer.SnapshotDeserializer;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.util.Properties;

@Configuration
@ConfigurationProperties(prefix = "kafka")
@Getter
@Setter
public class KafkaConfig {
    private String bootstrapServers;
    private ConsumerConfigProperties consumer = new ConsumerConfigProperties();
    private Topics topics = new Topics();

    @Bean
    public KafkaConsumer<String, HubEventAvro> hubEventConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Безопасное получение конфигурации с проверкой на null
        HubEventConsumer hubEventConfig = consumer != null ? consumer.getHubEvents() : null;

        if (hubEventConfig != null && hubEventConfig.getGroupId() != null) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, hubEventConfig.getGroupId());
        } else {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "analyzer-hub-events-group");
        }

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                hubEventConfig != null ? hubEventConfig.isEnableAutoCommit() : false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                hubEventConfig != null && hubEventConfig.getAutoOffsetReset() != null ?
                        hubEventConfig.getAutoOffsetReset() : "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                hubEventConfig != null ? hubEventConfig.getMaxPollRecords() : 10);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, HubEventDeserializer.class);

        return new KafkaConsumer<>(props);
    }

    @Bean
    public KafkaConsumer<String, SensorsSnapshotAvro> snapshotConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Безопасное получение конфигурации с проверкой на null
        SnapshotConsumer snapshotConfig = consumer != null ? consumer.getSnapshots() : null;

        if (snapshotConfig != null && snapshotConfig.getGroupId() != null) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, snapshotConfig.getGroupId());
        } else {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "analyzer-snapshots-group");
        }

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                snapshotConfig != null ? snapshotConfig.isEnableAutoCommit() : false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                snapshotConfig != null && snapshotConfig.getAutoOffsetReset() != null ?
                        snapshotConfig.getAutoOffsetReset() : "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                snapshotConfig != null ? snapshotConfig.getMaxPollRecords() : 10);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SnapshotDeserializer.class);

        return new KafkaConsumer<>(props);
    }

    @Getter
    @Setter
    public static class ConsumerConfigProperties {
        private HubEventConsumer hubEvents = new HubEventConsumer();
        private SnapshotConsumer snapshots = new SnapshotConsumer();
    }

    @Getter
    @Setter
    public static class HubEventConsumer {
        private String groupId = "analyzer-hub-events-group";
        private boolean enableAutoCommit = false;
        private String autoOffsetReset = "earliest";
        private int maxPollRecords = 10;
    }

    @Getter
    @Setter
    public static class SnapshotConsumer {
        private String groupId = "analyzer-snapshots-group";
        private boolean enableAutoCommit = false;
        private String autoOffsetReset = "earliest";
        private int maxPollRecords = 10;
    }

    @Getter
    @Setter
    public static class Topics {
        private String hubEvents = "telemetry.hubs.v1";
        private String snapshots = "telemetry.snapshots.v1";
    }
}