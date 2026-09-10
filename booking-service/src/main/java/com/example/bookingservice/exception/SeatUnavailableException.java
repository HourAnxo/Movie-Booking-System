package com.example.bookingservice.exception;

/**
 * Raised when seat-service refuses to reserve the requested seat, i.e.
 * somebody else booked it first. Mapped to 409 by
 * {@link GlobalExceptionHandler}.
 */
public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String message) {
        super(message);
    }
}
