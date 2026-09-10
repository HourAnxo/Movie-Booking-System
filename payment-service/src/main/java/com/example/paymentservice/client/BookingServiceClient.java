package com.example.paymentservice.client;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * payment-service's view of booking-service. This is the callback half of
 * the booking saga: a settled payment confirms its booking, a failed one
 * cancels it (which releases the seat).
 *
 * Note what an open breaker means here. These calls run after the payment
 * row has already changed state, so a failure leaves payment and booking
 * disagreeing until someone reconciles. Failing fast is still better than
 * blocking the caller for the full timeout on every request — but neither
 * call is safe to silently drop, which is why there is no fallback that
 * swallows the error.
 */
@Component
public class BookingServiceClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public BookingServiceClient(
            @LoadBalanced RestClient.Builder builder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.restClient = builder
                .baseUrl("http://BOOKING-SERVICE")
                .build();

        this.circuitBreaker = circuitBreakerFactory.create("booking-service");
    }

    public void confirmBooking(Integer bookingId) {

        circuitBreaker.run(
                () -> {
                    restClient.put()
                            .uri("/api/bookings/{id}/confirm", bookingId)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                },
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    public void cancelBooking(Integer bookingId) {

        circuitBreaker.run(
                () -> {
                    restClient.put()
                            .uri("/api/bookings/{id}/cancel", bookingId)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                },
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    private RuntimeException asRuntimeException(Throwable throwable) {

        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }

        return new IllegalStateException(
                "booking-service call failed: " + throwable.getMessage(),
                throwable
        );
    }
}
