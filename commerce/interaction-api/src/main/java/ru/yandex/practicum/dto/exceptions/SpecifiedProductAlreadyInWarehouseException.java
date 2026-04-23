package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class SpecifiedProductAlreadyInWarehouseException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public SpecifiedProductAlreadyInWarehouseException(UUID productId) {
        super("Product already in warehouse: " + productId);
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.userMessage = "Товар с таким описанием уже зарегистрирован на складе";
    }
}