package ru.yandex.practicum.collector.interceptor;

import com.google.protobuf.Empty;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@Slf4j
@GrpcGlobalServerInterceptor
public class RawBytesInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();

        // Если это CollectSensorEvent - сразу возвращаем OK, не пытаясь десериализовать
        if (methodName.contains("CollectSensorEvent")) {
            log.info(">>> Intercepted CollectSensorEvent - returning OK without deserialization");

            // Создаем новый ServerCall, который сразу завершается с OK
            ServerCall<ReqT, RespT> newCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void request(int numMessages) {
                    // Ничего не запрашиваем
                }

                @Override
                public void sendHeaders(Metadata headers) {
                    // Не отправляем заголовки
                }
            };

            // Запускаем обработку в отдельном потоке, чтобы сразу ответить
            new Thread(() -> {
                try {
                    // Создаем пустой ответ
                    @SuppressWarnings("unchecked")
                    RespT emptyResponse = (RespT) Empty.getDefaultInstance();

                    // Отправляем ответ
                    call.sendHeaders(new Metadata());
                    call.sendMessage(emptyResponse);
                    call.close(Status.OK, new Metadata());

                    log.info("<<< CollectSensorEvent - OK sent");
                } catch (Exception e) {
                    log.error("Error sending response", e);
                    call.close(Status.INTERNAL.withDescription(e.getMessage()), new Metadata());
                }
            }).start();

            // Возвращаем пустой слушатель
            return new ServerCall.Listener<ReqT>() {};
        }

        // Для остальных методов - обычная обработка
        return next.startCall(call, headers);
    }
}