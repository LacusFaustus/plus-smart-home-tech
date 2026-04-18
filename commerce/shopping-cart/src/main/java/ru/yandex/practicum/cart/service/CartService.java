package ru.yandex.practicum.cart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.exceptions.NotAuthorizedUserException;
import ru.yandex.practicum.dto.exceptions.NoProductsInShoppingCartException;
import ru.yandex.practicum.dto.exceptions.ProductInShoppingCartLowQuantityInWarehouse;
import ru.yandex.practicum.dto.exceptions.NoSpecifiedProductInWarehouseException;
import ru.yandex.practicum.dto.warehouse.BookedProductsDto;
import ru.yandex.practicum.enums.CartState;
import ru.yandex.practicum.client.WarehouseClient;
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
    private final WarehouseClient warehouseClient;

    // ==================== Публичные методы API ====================

    public ShoppingCartDto getCart(String username) {
        validateUsername(username);

        Cart cart = cartRepository.findByUsername(username)
                .orElseGet(() -> createNewCart(username));

        log.info("Retrieved cart for user: {}", username);
        return toDto(cart);
    }

    @Transactional
    public ShoppingCartDto addProducts(String username, Map<UUID, Long> newProducts) {
        validateUsername(username);
        validateProductsMap(newProducts);

        Cart cart = getOrCreateCart(username);
        validateCartIsActive(cart);

        // Формируем полную корзину с новыми товарами для проверки склада
        Map<UUID, Long> fullCartMap = getCurrentProducts(cart);
        newProducts.forEach((id, qty) -> fullCartMap.merge(id, qty, Long::sum));

        // Проверка наличия товаров на складе
        ShoppingCartDto checkCart = ShoppingCartDto.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(fullCartMap)
                .build();
        validateStockAvailability(checkCart);

        // Добавляем товары в корзину
        for (Map.Entry<UUID, Long> entry : newProducts.entrySet()) {
            addOrUpdateItem(cart, entry.getKey(), entry.getValue());
        }

        Cart saved = cartRepository.save(cart);
        log.info("Added products to cart for user {}: {}", username, newProducts.keySet());
        return toDto(saved);
    }

    @Transactional
    public ShoppingCartDto changeQuantity(String username, UUID productId, Long newQuantity) {
        validateUsername(username);
        validateProductId(productId);
        validateQuantity(newQuantity);

        Cart cart = getOrCreateCart(username);
        validateCartIsActive(cart);

        // Проверяем, что товар есть в корзине
        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Product {} not found in cart for user {}", productId, username);
                    return new NoProductsInShoppingCartException(productId);
                });

        // Обновляем количество (если 0 или null - удаляем)
        if (newQuantity == 0) {
            cart.getItems().remove(existingItem);
            cartItemRepository.delete(existingItem);
            log.info("Removed product {} from cart for user {}", productId, username);
        } else {
            existingItem.setQuantity(newQuantity);
            log.info("Changed quantity for user {}: productId={}, newQuantity={}", username, productId, newQuantity);
        }

        // Повторная проверка склада после изменения
        ShoppingCartDto checkCart = toDto(cart);
        validateStockAvailability(checkCart);

        Cart saved = cartRepository.save(cart);
        return toDto(saved);
    }

    @Transactional
    public ShoppingCartDto removeProducts(String username, List<UUID> productIds) {
        validateUsername(username);
        validateProductIdsList(productIds);

        Cart cart = getOrCreateCart(username);

        // Проверяем, что хотя бы один товар из списка есть в корзине
        boolean hasAnyProduct = cart.getItems().stream()
                .anyMatch(item -> productIds.contains(item.getProductId()));

        if (!hasAnyProduct) {
            log.warn("None of the products {} found in cart for user {}", productIds, username);
            throw new NoProductsInShoppingCartException(productIds.get(0));
        }

        // Удаляем товары
        int deletedCount = cartItemRepository.deleteByCartIdAndProductIds(
                cart.getShoppingCartId(), productIds
        );

        cart.getItems().removeIf(item -> productIds.contains(item.getProductId()));

        log.info("Removed {} products from cart for user {}: {}", deletedCount, username, productIds);
        return toDto(cart);
    }

    @Transactional
    public void deactivateCart(String username) {
        validateUsername(username);

        Cart cart = getOrCreateCart(username);
        cart.setState(CartState.DEACTIVATE);
        cartRepository.save(cart);

        log.info("Deactivated cart for user: {}", username);
    }

    // ==================== Приватные вспомогательные методы ====================

    private Cart createNewCart(String username) {
        Cart cart = Cart.builder()
                .username(username)
                .state(CartState.ACTIVE)
                .items(new ArrayList<>())
                .build();
        log.info("Created new cart for user: {}", username);
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

    // ==================== Методы валидации ====================

    /**
     * Проверяет, что имя пользователя не пустое.
     * Согласно ТЗ: "Имя пользователя не должно быть пустым"
     */
    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            log.warn("Validation failed: username is empty or null");
            throw new NotAuthorizedUserException(username);
        }
    }

    /**
     * Проверяет, что корзина активна (не деактивирована).
     * Согласно ТЗ: деактивированную корзину нельзя использовать для заказа
     */
    private void validateCartIsActive(Cart cart) {
        if (cart.getState() == CartState.DEACTIVATE) {
            log.warn("Validation failed: cart is deactivated for user {}", cart.getUsername());
            throw new RuntimeException("Cart is deactivated. Cannot add or change products in deactivated cart.");
        }
    }

    /**
     * Проверяет, что карта товаров не пуста.
     */
    private void validateProductsMap(Map<UUID, Long> products) {
        if (products == null || products.isEmpty()) {
            log.warn("Validation failed: products map is empty or null");
            throw new IllegalArgumentException("Products map cannot be empty");
        }

        // Проверка, что все количества положительные
        for (Map.Entry<UUID, Long> entry : products.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                log.warn("Validation failed: invalid quantity {} for product {}", entry.getValue(), entry.getKey());
                throw new IllegalArgumentException("Product quantity must be positive. Provided: " + entry.getValue());
            }
        }
    }

    /**
     * Проверяет, что идентификатор товара не null.
     */
    private void validateProductId(UUID productId) {
        if (productId == null) {
            log.warn("Validation failed: productId is null");
            throw new IllegalArgumentException("Product ID cannot be null");
        }
    }

    /**
     * Проверяет, что количество товара корректно.
     * newQuantity = 0 означает удаление товара (допустимо)
     * newQuantity > 0 означает установку нового количества
     */
    private void validateQuantity(Long newQuantity) {
        if (newQuantity == null) {
            log.warn("Validation failed: quantity is null");
            throw new IllegalArgumentException("Quantity cannot be null");
        }
        if (newQuantity < 0) {
            log.warn("Validation failed: quantity is negative: {}", newQuantity);
            throw new IllegalArgumentException("Quantity cannot be negative. Provided: " + newQuantity);
        }
    }

    /**
     * Проверяет, что список идентификаторов товаров не пуст.
     */
    private void validateProductIdsList(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            log.warn("Validation failed: productIds list is empty or null");
            throw new IllegalArgumentException("Product IDs list cannot be empty");
        }

        // Проверка, что все ID не null
        for (UUID id : productIds) {
            if (id == null) {
                log.warn("Validation failed: productId in list is null");
                throw new IllegalArgumentException("Product ID in list cannot be null");
            }
        }
    }

    /**
     * Проверяет доступность товаров на складе.
     * Вызывает warehouse сервис через Feign клиент.
     *
     * @throws ProductInShoppingCartLowQuantityInWarehouse если товара недостаточно на складе
     * @throws NoSpecifiedProductInWarehouseException если товар не найден на складе
     * @throws RuntimeException если корзина пуста
     */
    private void validateStockAvailability(ShoppingCartDto cart) {
        if (cart.getProducts() == null || cart.getProducts().isEmpty()) {
            log.warn("Stock validation skipped: cart is empty");
            throw new RuntimeException("Cannot validate stock for empty cart");
        }

        log.debug("Validating stock availability for cart: {}", cart.getShoppingCartId());

        // Вызов warehouse сервиса через Feign клиент
        // При недоступности warehouse сработает CircuitBreaker (WarehouseFallback)
        BookedProductsDto result = warehouseClient.checkProductQuantityEnoughForShoppingCart(cart);

        log.debug("Stock validation passed for cart {}. Weight: {}, Volume: {}, Fragile: {}",
                cart.getShoppingCartId(),
                result.getDeliveryWeight(),
                result.getDeliveryVolume(),
                result.getFragile());
    }
}