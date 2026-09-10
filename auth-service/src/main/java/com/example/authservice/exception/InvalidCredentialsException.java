package com.example.authservice.exception;

/**
 * Thrown for a bad username/password pair or an invalid, expired, or
 * wrong-type token. Mapped to 401 by {@link GlobalExceptionHandler}.
 * The message is deliberately vague so it cannot be used to probe which
 * usernames exist.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
