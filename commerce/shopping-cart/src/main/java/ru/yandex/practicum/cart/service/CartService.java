package ru.yandex.practicum.cart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.shopping-cart.ShoppingCartDto;
import ru.yandex.practicum.dto.exceptions.NotAuthorizedUserException;
import ru.yandex.practicum.dto.exceptions.NoProductsInShoppingCartException;
import ru.yandex.practicum.enums.CartState;
import ru.yandex.practicum.cart.client.WarehouseFeignClient;
import ru.yandex.practicum.cart.model.Cart;
import ru.yandex.practicum.cart.model.CartItem;
import ru.yandex.practicum.cart.repository.CartRepository;
import ru.yandex.practicum.cart.repository.CartItemRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final WarehouseFeignClient warehouseClient;

    public ShoppingCartDto getCart(String username) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(username);
        }

        Cart cart = cartRepository.findByUsername(username)
                .orElseGet(() -> createNewCart(username));

        return toDto(cart);
    }

    @Transactional
    public ShoppingCartDto addProducts(String username, Map<UUID, Long> newProducts) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(username);
        }

        Cart cart = getOrCreateCart(username);

        if (cart.getState() == CartState.DEACTIVATE) {
            throw new RuntimeException("Cart is deactivated for user: " + username);
        }

        // Формируем полную корзину для проверки
        Map<UUID, Long> fullCart = getCurrentProducts(cart);
        newProducts.forEach((id, qty) -> fullCart.merge(id, qty, Long::sum));

        // Проверяем наличие на складе (с Circuit Breaker)
        ShoppingCartDto checkCart = ShoppingCartDto.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(fullCart)
                .build();
        warehouseClient.checkProductQuantityEnoughForShoppingCart(checkCart);

        // Добавляем товары
        for (Map.Entry<UUID, Long> entry : newProducts.entrySet()) {
            addOrUpdateItem(cart, entry.getKey(), entry.getValue());
        }

        Cart saved = cartRepository.save(cart);
        log.info("Added products to cart for user {}: {}", username, newProducts.keySet());
        return toDto(saved);
    }

    @Transactional
    public ShoppingCartDto changeQuantity(String username, UUID productId, Long newQuantity) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(username);
        }

        Cart cart = getOrCreateCart(username);

        if (cart.getState() == CartState.DEACTIVATE) {
            throw new RuntimeException("Cart is deactivated for user: " + username);
        }

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new NoProductsInShoppingCartException(productId));

        if (newQuantity == null || newQuantity <= 0) {
            cart.getItems().remove(existingItem);
            cartItemRepository.delete(existingItem);
        } else {
            existingItem.setQuantity(newQuantity);
        }

        Cart saved = cartRepository.save(cart);
        log.info("Changed quantity for user {}: productId={}, newQuantity={}", username, productId, newQuantity);
        return toDto(saved);
    }

    @Transactional
    public ShoppingCartDto removeProducts(String username, List<UUID> productIds) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(username);
        }

        Cart cart = getOrCreateCart(username);

        int deletedCount = cartItemRepository.deleteByCartIdAndProductIds(
                cart.getShoppingCartId(), productIds
        );

        if (deletedCount == 0 && !productIds.isEmpty()) {
            throw new NoProductsInShoppingCartException(productIds.get(0));
        }

        // Обновляем кэш
        cart.getItems().removeIf(item -> productIds.contains(item.getProductId()));

        log.info("Removed products from cart for user {}: {}", username, productIds);
        return toDto(cart);
    }

    @Transactional
    public void deactivateCart(String username) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(username);
        }

        Cart cart = getOrCreateCart(username);
        cart.setState(CartState.DEACTIVATE);
        cartRepository.save(cart);
        log.info("Deactivated cart for user: {}", username);
    }

    private Cart createNewCart(String username) {
        Cart cart = Cart.builder()
                .username(username)
                .state(CartState.ACTIVE)
                .items(new ArrayList<>())
                .build();
        return cartRepository.save(cart);
    }

    private Cart getOrCreateCart(String username) {
        return cartRepository.findByUsername(username)
                .orElseGet(() -> createNewCart(username));
    }

    private void addOrUpdateItem(Cart cart, UUID productId, Long quantity) {
        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productId(productId)
                    .quantity(quantity)
                    .build();
            cart.getItems().add(newItem);
        }
    }

    private Map<UUID, Long> getCurrentProducts(Cart cart) {
        return cart.getItems().stream()
                .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));
    }

    private ShoppingCartDto toDto(Cart cart) {
        return ShoppingCartDto.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(cart.getItems().stream()
                        .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity)))
                .build();
    }
}