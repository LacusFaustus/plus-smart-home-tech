package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class NoSpecifiedProductInWarehouseException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public NoSpecifiedProductInWarehouseException(UUID productId) {
        super("Product not found in warehouse: " + productId);
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.userMessage = "Нет информации о товаре на складе";
    }
}