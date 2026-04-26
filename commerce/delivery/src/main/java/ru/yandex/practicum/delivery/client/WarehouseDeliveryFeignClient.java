package ru.yandex.practicum.delivery.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.warehouse.ShippedToDeliveryRequest;

@FeignClient(name = "warehouse")
public interface WarehouseDeliveryFeignClient {

    @PostMapping("/api/v1/warehouse/shipped")
    void shippedToDelivery(@RequestBody ShippedToDeliveryRequest request);
}