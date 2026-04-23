package ru.yandex.practicum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.shoppingcart.ChangeProductQuantityRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "shopping-cart")
public interface ShoppingCartClient {

    @GetMapping("/api/v1/shopping-cart")
    ShoppingCartDto getShoppingCart(@RequestParam("username") String username);

    @PutMapping("/api/v1/shopping-cart")
    ShoppingCartDto addProductToShoppingCart(
            @RequestParam("username") String username,
            @RequestBody Map<String, Long> products
    );

    @DeleteMapping("/api/v1/shopping-cart")
    void deactivateCurrentShoppingCart(@RequestParam("username") String username);

    @PostMapping("/api/v1/shopping-cart/remove")
    ShoppingCartDto removeFromShoppingCart(
            @RequestParam("username") String username,
            @RequestBody List<UUID> productIds
    );

    @PostMapping("/api/v1/shopping-cart/change-quantity")
    ShoppingCartDto changeProductQuantity(
            @RequestParam("username") String username,
            @RequestBody ChangeProductQuantityRequest request
    );
}