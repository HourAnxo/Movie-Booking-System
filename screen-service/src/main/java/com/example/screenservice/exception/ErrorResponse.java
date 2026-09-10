package com.example.screenservice.exception;

import java.time.LocalDateTime;

/**
 * Error body returned by {@link GlobalExceptionHandler} for every
 * handled failure, so all services in the system fail the same shape.
 */
public record ErrorResponse(

        LocalDateTime timestamp,

        int status,

        String error,

        String message,

        String path

) {
}
