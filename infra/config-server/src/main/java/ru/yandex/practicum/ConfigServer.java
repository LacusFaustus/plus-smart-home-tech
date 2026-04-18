package ru.yandex.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ConfigServer {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServer.class, args);
    }

    @GetMapping("/actuator/health")
    public Health health() {
        return new Health("UP");
    }

    @GetMapping("/actuator/info")
    public Info info() {
        return new Info("config-server");
    }

    static class Health {
        private final String status;
        Health(String status) { this.status = status; }
        public String getStatus() { return status; }
    }

    static class Info {
        private final String name;
        Info(String name) { this.name = name; }
        public String getName() { return name; }
    }
}