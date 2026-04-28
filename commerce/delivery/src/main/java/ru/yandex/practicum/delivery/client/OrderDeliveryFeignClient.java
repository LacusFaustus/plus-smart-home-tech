package ru.yandex.practicum.delivery.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "order")
public interface OrderDeliveryFeignClient {

    @PostMapping("/api/v1/order/delivery")
    void delivery(@RequestBody UUID orderId);

    @PostMapping("/api/v1/order/delivery/failed")
    void deliveryFailed(@RequestBody UUID orderId);
}