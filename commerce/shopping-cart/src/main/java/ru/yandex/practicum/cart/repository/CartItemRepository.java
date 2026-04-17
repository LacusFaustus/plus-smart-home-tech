package ru.yandex.practicum.cart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.cart.model.CartItem;

import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.shoppingCartId = :cartId AND ci.productId IN :productIds")
    int deleteByCartIdAndProductIds(@Param("cartId") UUID cartId, @Param("productIds") java.util.Collection<UUID> productIds);
}