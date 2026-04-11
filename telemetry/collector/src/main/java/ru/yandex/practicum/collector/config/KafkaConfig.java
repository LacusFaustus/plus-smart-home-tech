package ru.yandex.practicum.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.collector.serializer.GeneralAvroSerializer;

import java.util.Properties;

@Configuration
@ConfigurationProperties(prefix = "kafka")
@Getter
@Setter
public class KafkaConfig {
    private String bootstrapServers;
    private Topics topics;

    @Bean
    public Producer<String, SpecificRecordBase> kafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GeneralAvroSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    @Getter
    @Setter
    public static class Topics {
        private String sensors;
        private String hubs;
    }
}