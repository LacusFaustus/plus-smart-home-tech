package ru.yandex.practicum.hubrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HubRouterApplication {
    public static void main(String[] args) {
        SpringApplication.run(HubRouterApplication.class, args);
    }
}