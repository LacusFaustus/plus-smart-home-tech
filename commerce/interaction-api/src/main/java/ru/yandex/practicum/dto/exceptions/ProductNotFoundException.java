package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class ProductNotFoundException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public ProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
        this.httpStatus = HttpStatus.NOT_FOUND;
        this.userMessage = "Товар не найден";
    }
}