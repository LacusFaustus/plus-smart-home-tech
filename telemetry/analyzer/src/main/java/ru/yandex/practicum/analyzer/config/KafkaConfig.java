package ru.yandex.practicum.analyzer.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.avro.specific.SpecificRecordBase;
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
    private ConsumerConfigProperties consumer;
    private Topics topics;

    @Bean
    public KafkaConsumer<String, HubEventAvro> hubEventConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.getHubEvents().getGroupId());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.getHubEvents().isEnableAutoCommit());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.getHubEvents().getAutoOffsetReset());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getHubEvents().getMaxPollRecords());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, HubEventDeserializer.class);

        return new KafkaConsumer<>(props);
    }

    @Bean
    public KafkaConsumer<String, SensorsSnapshotAvro> snapshotConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.getSnapshots().getGroupId());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.getSnapshots().isEnableAutoCommit());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.getSnapshots().getAutoOffsetReset());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getSnapshots().getMaxPollRecords());
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
        private String groupId;
        private boolean enableAutoCommit;
        private String autoOffsetReset;
        private int maxPollRecords;
    }

    @Getter
    @Setter
    public static class SnapshotConsumer {
        private String groupId;
        private boolean enableAutoCommit;
        private String autoOffsetReset;
        private int maxPollRecords;
    }

    @Getter
    @Setter
    public static class Topics {
        private String hubEvents;
        private String snapshots;
    }
}