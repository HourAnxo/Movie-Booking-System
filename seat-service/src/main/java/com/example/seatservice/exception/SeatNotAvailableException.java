package com.example.seatservice.exception;

/**
 * Thrown when a seat cannot be reserved because it is not AVAILABLE —
 * someone else took it, or it is BLOCKED. Mapped to 409 by
 * {@link GlobalExceptionHandler}.
 */
public class SeatNotAvailableException extends RuntimeException {

    public SeatNotAvailableException(String message) {
        super(message);
    }
}
