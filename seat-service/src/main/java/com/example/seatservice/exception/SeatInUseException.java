package com.example.seatservice.exception;

/**
 * Thrown when a seat cannot be deleted because a live booking still holds
 * it (a reservation row, or a pre-V4 BOOKED status). Deleting it would leave
 * that booking pointing at a seat that no longer exists. Mapped to 409 by
 * {@link GlobalExceptionHandler}.
 */
public class SeatInUseException extends RuntimeException {

    public SeatInUseException(String message) {
        super(message);
    }
}
