package com.example.paymentservice.exception;

import org.springframework.web.client.RestClientException;

/**
 * The Bakong Open API answered, but with an error we cannot interpret as
 * "paid" or "not paid yet" — an expired token, for example.
 *
 * A RestClientException on purpose, so the existing mapping makes it a 502
 * (this service is fine, a dependency is not) and the circuit breaker
 * counts it as a failure.
 */
public class BakongApiException extends RestClientException {

    public BakongApiException(String message) {
        super(message);
    }
}
