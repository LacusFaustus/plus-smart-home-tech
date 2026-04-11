package ru.yandex.practicum.collector.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@GrpcGlobalServerInterceptor
public class DebugInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        log.info(">>> gRPC call: {}", methodName);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                next.startCall(call, headers)) {

            @Override
            public void onMessage(ReqT message) {
                // Логируем тип сообщения и его строковое представление
                log.info("Message type: {}", message.getClass().getName());
                log.info("Message content: {}", message.toString());

                // Пробуем определить реальную структуру
                if (message instanceof byte[]) {
                    byte[] bytes = (byte[]) message;
                    log.info("Raw bytes length: {}", bytes.length);
                    // Выводим первые 50 байт в hex
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(50, bytes.length); i++) {
                        hex.append(String.format("%02X ", bytes[i]));
                    }
                    log.info("First 50 bytes (hex): {}", hex.toString());
                }

                super.onMessage(message);
            }
        };
    }
}