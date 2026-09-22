package com.example.bookingservice.client;

import com.example.bookingservice.dto.ReservedSeatDTO;
import com.example.bookingservice.exception.SeatUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * booking-service's view of seat-service. Resolves SEAT-SERVICE through
 * Eureka, so no host or port is hardcoded.
 *
 * Both calls go through a circuit breaker, and neither has a fallback that
 * invents a result. There is no safe made-up answer here: pretending a
 * reservation succeeded double-books the seat, and pretending it failed
 * loses a sale. The breaker's only job is to fail fast rather than sit on a
 * timeout once seat-service is clearly down.
 */
@Component
public class SeatServiceClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public SeatServiceClient(
            @LoadBalanced RestClient.Builder builder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.restClient = builder
                .baseUrl("http://SEAT-SERVICE")
                .build();

        this.circuitBreaker = circuitBreakerFactory.create("seat-service");
    }

    /**
     * Marks the seat BOOKED. seat-service answers 409 when the seat is
     * already taken; that is translated here so the caller sees a domain
     * exception rather than an HTTP one.
     *
     * Returns the reserved seat so the booking can be priced from it.
     */
    public ReservedSeatDTO reserveSeat(Integer seatId) {

        return circuitBreaker.run(
                () -> doReserveSeat(seatId),
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    private ReservedSeatDTO doReserveSeat(Integer seatId) {

        try {

            return restClient.put()
                    .uri("/api/seats/{id}/reserve", seatId)
                    .retrieve()
                    .body(ReservedSeatDTO.class);

        } catch (RestClientResponseException ex) {

            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new SeatUnavailableException(
                        "Seat " + seatId + " is already booked"
                );
            }

            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new SeatUnavailableException(
                        "Seat " + seatId + " does not exist"
                );
            }

            throw ex;
        }
    }

    /**
     * Compensating action — returns the seat to AVAILABLE. seat-service
     * makes this idempotent, so a retry is safe.
     *
     * When the breaker is open this throws, and BookingServiceImpl's
     * safeReleaseSeat swallows it. That is the intended behaviour: a
     * cancellation must not be undone because seat-service is down, and the
     * stuck seat is logged for reconciliation.
     */
    public void releaseSeat(Integer seatId) {

        circuitBreaker.run(
                () -> {
                    restClient.put()
                            .uri("/api/seats/{id}/release", seatId)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                },
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    /**
     * Rethrows the original failure rather than letting Spring Cloud wrap it
     * in NoFallbackAvailableException. The exception mapping downstream —
     * SeatUnavailableException to 409, RestClientException to 502 — depends
     * on the original type surviving the breaker.
     */
    private RuntimeException asRuntimeException(Throwable throwable) {

        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }

        return new IllegalStateException(
                "seat-service call failed: " + throwable.getMessage(),
                throwable
        );
    }
}
