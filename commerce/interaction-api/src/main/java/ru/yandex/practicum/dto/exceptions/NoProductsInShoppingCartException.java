package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class NoProductsInShoppingCartException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public NoProductsInShoppingCartException(UUID productId) {
        super("Product not found in cart: " + productId);
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.userMessage = "Нет искомых товаров в корзине";
    }
}