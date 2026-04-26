package ru.yandex.practicum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.delivery.DeliveryDto;
import ru.yandex.practicum.dto.order.OrderDto;

import java.util.UUID;

@FeignClient(name = "delivery")
public interface DeliveryClient {

    @PostMapping("/api/v1/delivery")
    DeliveryDto planDelivery(@RequestBody DeliveryDto delivery);

    @PostMapping("/api/v1/delivery/cost")
    Double deliveryCost(@RequestBody OrderDto order);

    @PostMapping("/api/v1/delivery/successful")
    void deliverySuccessful(@RequestBody UUID orderId);

    @PostMapping("/api/v1/delivery/picked")
    void deliveryPicked(@RequestBody UUID orderId);

    @PostMapping("/api/v1/delivery/failed")
    void deliveryFailed(@RequestBody UUID orderId);
}