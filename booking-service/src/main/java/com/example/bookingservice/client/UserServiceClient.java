package com.example.bookingservice.client;

import com.example.bookingservice.exception.InvalidBookingReferenceException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * booking-service's view of user-service, used only to confirm that the
 * user a booking is being made for actually exists.
 */
@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public UserServiceClient(
            @LoadBalanced RestClient.Builder builder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.restClient = builder
                .baseUrl("http://USER-SERVICE")
                .build();

        this.circuitBreaker = circuitBreakerFactory.create("user-service");
    }

    /**
     * Throws if the user does not exist.
     *
     * A user-service outage propagates (502) rather than being swallowed —
     * booking-service cannot tell "no such user" from "cannot check right
     * now", and letting bookings through unchecked would defeat the purpose
     * of validating at all.
     *
     * The breaker does not soften that. There is deliberately no fallback
     * returning "assume the user exists": that would be a fail-open check,
     * which is the same as no check. An open circuit means new bookings are
     * refused until user-service recovers, and that is the correct trade.
     */
    public void requireUserExists(Integer userId) {

        if (userId == null) {
            throw new InvalidBookingReferenceException(
                    "userId is required"
            );
        }

        circuitBreaker.run(
                () -> {
                    doRequireUserExists(userId);
                    return null;
                },
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    private void doRequireUserExists(Integer userId) {

        try {

            restClient.get()
                    .uri("/api/users/{id}", userId)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException ex) {

            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new InvalidBookingReferenceException(
                        "No user with ID: " + userId
                );
            }

            throw ex;
        }
    }

    /**
     * Rethrows the original so the downstream exception mapping still sees
     * InvalidBookingReferenceException (400) and RestClientException (502)
     * rather than a NoFallbackAvailableException wrapper.
     */
    private RuntimeException asRuntimeException(Throwable throwable) {

        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }

        return new IllegalStateException(
                "user-service call failed: " + throwable.getMessage(),
                throwable
        );
    }
}
