package ru.yandex.practicum.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "order")
public interface OrderPaymentFeignClient {

    @PostMapping("/api/v1/order/payment")
    void payment(@RequestBody UUID orderId);

    @PostMapping("/api/v1/order/payment/failed")
    void paymentFailed(@RequestBody UUID orderId);
}