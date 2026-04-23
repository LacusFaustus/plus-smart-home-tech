package ru.yandex.practicum.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "ru.yandex.practicum")
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "ru.yandex.practicum.client")
@EnableJpaRepositories(basePackages = "ru.yandex.practicum.warehouse.repository")
public class WarehouseApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseApplication.class, args);
    }
}