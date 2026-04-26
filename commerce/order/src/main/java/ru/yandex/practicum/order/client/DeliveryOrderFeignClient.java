package ru.yandex.practicum.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.delivery.DeliveryDto;
import ru.yandex.practicum.dto.order.OrderDto;

@FeignClient(name = "delivery")
public interface DeliveryOrderFeignClient {

    @PostMapping("/api/v1/delivery")
    DeliveryDto planDelivery(@RequestBody DeliveryDto delivery);

    @PostMapping("/api/v1/delivery/cost")
    Double deliveryCost(@RequestBody OrderDto order);
}