package com.example.paymentservice.exception;

/**
 * The caller is authenticated but is not the owner of the booking they are
 * trying to pay for. Mapped to 403 by {@link GlobalExceptionHandler}.
 */
public class PaymentForbiddenException extends RuntimeException {

    public PaymentForbiddenException(String message) {
        super(message);
    }
}
