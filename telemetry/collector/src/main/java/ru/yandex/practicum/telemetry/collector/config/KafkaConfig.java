package ru.yandex.practicum.telemetry.collector.config;

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

    @Getter
    @Setter
    public static class Topics {
        private String sensors;
        private String hubs;
    }
}