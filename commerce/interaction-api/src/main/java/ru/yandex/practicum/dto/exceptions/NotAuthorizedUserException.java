package ru.yandex.practicum.dto.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class NotAuthorizedUserException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String userMessage;

    public NotAuthorizedUserException(String username) {
        super("User not authorized: " + username);
        this.httpStatus = HttpStatus.UNAUTHORIZED;
        this.userMessage = "Имя пользователя не должно быть пустым";
    }
}