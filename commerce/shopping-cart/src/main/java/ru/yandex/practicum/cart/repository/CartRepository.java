package ru.yandex.practicum.cart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.cart.model.Cart;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUsername(String username);
    boolean existsByUsername(String username);
}