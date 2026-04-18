package ru.yandex.practicum.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import ru.yandex.practicum.client.WarehouseClient;
import ru.yandex.practicum.cart.fallback.WarehouseFallback;

@FeignClient(name = "warehouse", fallback = WarehouseFallback.class)
public interface WarehouseFeignClient extends WarehouseClient {
}