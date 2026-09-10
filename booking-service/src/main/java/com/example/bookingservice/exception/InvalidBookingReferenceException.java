package com.example.bookingservice.exception;

/**
 * A booking referenced something that does not exist in the service that
 * owns it — currently a userId with no matching user-service record.
 *
 * Mapped to 400 rather than 404 by {@link GlobalExceptionHandler}: the
 * bookings endpoint itself exists, it is the request body that is wrong,
 * and a 404 on POST /api/bookings reads as "no such endpoint".
 */
public class InvalidBookingReferenceException extends RuntimeException {

    public InvalidBookingReferenceException(String message) {
        super(message);
    }
}
