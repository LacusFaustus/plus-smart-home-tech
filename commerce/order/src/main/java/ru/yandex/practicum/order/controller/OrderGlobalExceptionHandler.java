package ru.yandex.practicum.order.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yandex.practicum.dto.exceptions.NoOrderFoundException;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class OrderGlobalExceptionHandler {

    private static final String ERROR_ORDER_NOT_FOUND = "Order not found";

    @ExceptionHandler(NoOrderFoundException.class)
    public ResponseEntity<ErrorDto> handleNoOrderFound(NoOrderFoundException ex) {
        log.error("{}: {}", ERROR_ORDER_NOT_FOUND, ex.getMessage());
        ErrorDto error = ErrorDto.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error(ERROR_ORDER_NOT_FOUND)
                .message(ex.getUserMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}