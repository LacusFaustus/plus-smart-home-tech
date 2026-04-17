package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class ProductInShoppingCartLowQuantityInWarehouse extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;
    private final UUID productId;
    private final Long availableQuantity;
    private final Long requestedQuantity;

    public ProductInShoppingCartLowQuantityInWarehouse(UUID productId, Long available, Long requested) {
        super(String.format("Not enough stock for product %s: available=%d, requested=%d", productId, available, requested));
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.userMessage = "Товара недостаточно на складе";
        this.productId = productId;
        this.availableQuantity = available;
        this.requestedQuantity = requested;
    }
}