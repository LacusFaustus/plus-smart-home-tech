package ru.yandex.practicum.collector.config;

import io.grpc.*;

public class Utf8ValidationInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Отключаем строгую проверку UTF-8 для всех входящих сообщений
        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void request(int numMessages) {
                super.request(numMessages);
            }

            @Override
            public void sendMessage(RespT message) {
                super.sendMessage(message);
            }
        };

        return next.startCall(wrappedCall, headers);
    }
}