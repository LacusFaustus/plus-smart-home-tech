package ru.yandex.practicum.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.yandex.practicum.dto.shoppingstore.ProductDto;

import java.util.UUID;

@FeignClient(name = "shopping-store")
public interface ShoppingStorePaymentFeignClient {

    @GetMapping("/api/v1/shopping-store/{productId}")
    ProductDto getProduct(@PathVariable("productId") UUID productId);
}