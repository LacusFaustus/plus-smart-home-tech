package ru.yandex.practicum.cart.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.client.ShoppingCartClient;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.shoppingcart.ChangeProductQuantityRequest;
import ru.yandex.practicum.cart.service.CartService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shopping-cart")
@RequiredArgsConstructor
@Slf4j
public class CartController implements ShoppingCartClient {
    private final CartService cartService;

    @Override
    @GetMapping
    public ShoppingCartDto getShoppingCart(@RequestParam("username") String username) {
        log.info("GET /api/v1/shopping-cart: username={}", username);
        return cartService.getCart(username);
    }

    @Override
    @PutMapping
    public ShoppingCartDto addProductToShoppingCart(
            @RequestParam("username") String username,
            @RequestBody Map<String, Long> products) {
        log.info("PUT /api/v1/shopping-cart: username={}, products={}", username, products);
        Map<UUID, Long> converted = convertKeysToUUID(products);
        return cartService.addProducts(username, converted);
    }

    @Override
    @DeleteMapping
    public void deactivateCurrentShoppingCart(@RequestParam("username") String username) {
        log.info("DELETE /api/v1/shopping-cart: username={}", username);
        cartService.deactivateCart(username);
    }

    @Override
    @PostMapping("/remove")
    public ShoppingCartDto removeFromShoppingCart(
            @RequestParam("username") String username,
            @RequestBody List<UUID> productIds) {
        log.info("POST /api/v1/shopping-cart/remove: username={}, productIds={}", username, productIds);
        return cartService.removeProducts(username, productIds);
    }

    @Override
    @PostMapping("/change-quantity")
    public ShoppingCartDto changeProductQuantity(
            @RequestParam("username") String username,
            @RequestBody ChangeProductQuantityRequest request) {
        log.info("POST /api/v1/shopping-cart/change-quantity: username={}, request={}", username, request);
        return cartService.changeQuantity(username, request.getProductId(), request.getNewQuantity());
    }

    private Map<UUID, Long> convertKeysToUUID(Map<String, Long> stringKeyMap) {
        Map<UUID, Long> result = new java.util.HashMap<>();
        for (Map.Entry<String, Long> entry : stringKeyMap.entrySet()) {
            result.put(UUID.fromString(entry.getKey()), entry.getValue());
        }
        return result;
    }
}