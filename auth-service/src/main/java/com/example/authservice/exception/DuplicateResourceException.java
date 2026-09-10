package com.example.authservice.exception;

/**
 * Thrown when creating something that already exists (duplicate username,
 * email, ...). Mapped to 409 by {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
