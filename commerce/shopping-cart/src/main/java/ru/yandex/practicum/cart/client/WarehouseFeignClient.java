package ru.yandex.practicum.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import ru.yandex.practicum.client.WarehouseClient;

@FeignClient(name = "warehouse", fallback = WarehouseFallback.class)
public interface WarehouseFeignClient extends WarehouseClient {
}