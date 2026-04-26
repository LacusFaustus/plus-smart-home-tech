package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class NotEnoughInfoInOrderToCalculateException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public NotEnoughInfoInOrderToCalculateException(UUID orderId, String missingField) {
        super("Not enough info in order " + orderId + ": missing " + missingField);
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.userMessage = "Недостаточно информации в заказе для расчёта";
    }
}