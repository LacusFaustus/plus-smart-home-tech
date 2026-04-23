package ru.yandex.practicum.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "ru.yandex.practicum")
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "ru.yandex.practicum.client")
@EnableJpaRepositories(basePackages = "ru.yandex.practicum.store.repository")
public class ShoppingStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShoppingStoreApplication.class, args);
    }
}