package com.example.paymentservice.exception;

/**
 * The request is well-formed but the payment or booking is not in a state
 * that allows it — paying a booking that is already confirmed or cancelled,
 * or settling a payment that is no longer PENDING. Mapped to 409 by
 * {@link GlobalExceptionHandler}: the client should reload, not retry.
 */
public class PaymentStateException extends RuntimeException {

    public PaymentStateException(String message) {
        super(message);
    }
}
