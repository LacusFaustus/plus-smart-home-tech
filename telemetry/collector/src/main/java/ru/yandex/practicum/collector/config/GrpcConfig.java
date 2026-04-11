package ru.yandex.practicum.collector.config;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    @GrpcGlobalServerInterceptor
    public Utf8ValidationInterceptor utf8ValidationInterceptor() {
        return new Utf8ValidationInterceptor();
    }
}