package ru.yandex.practicum.payment.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yandex.practicum.dto.exceptions.NoOrderFoundException;
import ru.yandex.practicum.dto.exceptions.NotEnoughInfoInOrderToCalculateException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NoSuchElementException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return Map.of("error", "Resource not found", "message", ex.getMessage());
    }

    @ExceptionHandler(NoOrderFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNoOrder(NoOrderFoundException ex) {
        log.error("Order not found: {}", ex.getMessage());
        return Map.of("error", "Order not found", "message", ex.getUserMessage());
    }

    @ExceptionHandler(NotEnoughInfoInOrderToCalculateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNotEnoughInfo(NotEnoughInfoInOrderToCalculateException ex) {
        log.error("Not enough info: {}", ex.getMessage());
        return Map.of("error", "Not enough info", "message", ex.getUserMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        log.error("Bad request: {}", ex.getMessage());
        return Map.of("error", "Bad request", "message", ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleRuntime(RuntimeException ex) {
        log.error("Internal error: {}", ex.getMessage(), ex);
        return Map.of("error", "Internal server error", "message", ex.getMessage());
    }
}