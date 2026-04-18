package ru.yandex.practicum.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(
        scanBasePackages = "ru.yandex.practicum",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "ru.yandex.practicum.client")
public class WarehouseApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseApplication.class, args);
    }
}