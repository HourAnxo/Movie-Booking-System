package com.example.bookingservice.exception;

/**
 * The showtime exists but can no longer be booked because it has already
 * started. Mapped to 400 by {@link GlobalExceptionHandler}, not 409: the
 * client's 409 handling means "reload the seat grid and pick another seat",
 * which can't help here.
 */
public class ShowtimeNotBookableException extends RuntimeException {

    public ShowtimeNotBookableException(String message) {
        super(message);
    }
}
