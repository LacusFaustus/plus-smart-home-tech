package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class NoOrderFoundException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public NoOrderFoundException(UUID orderId) {
        super("Order not found: " + orderId);
        this.httpStatus = HttpStatus.NOT_FOUND;
        this.userMessage = "Заказ не найден";
    }
}