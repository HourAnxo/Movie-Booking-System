package com.example.showtimeservice.exception;

/**
 * Thrown when a lookup by id finds nothing. Mapped to 404 by
 * {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(
            String resource,
            Object id
    ) {
        super(resource + " not found with ID: " + id);
    }
}
