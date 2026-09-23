package com.example.paymentservice.exception;

/**
 * BAKONG_API_TOKEN or BAKONG_ACCOUNT_ID is not set. Mapped to 503: the
 * service is up, but it cannot take Bakong payments until an operator
 * configures it — and it must never hand out a QR it could not verify.
 */
public class BakongNotConfiguredException extends RuntimeException {

    public BakongNotConfiguredException() {
        super("Bakong payments are not configured: set BAKONG_API_TOKEN "
                + "and BAKONG_ACCOUNT_ID for payment-service");
    }
}
