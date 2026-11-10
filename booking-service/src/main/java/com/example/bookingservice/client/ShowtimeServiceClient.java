package com.example.bookingservice.client;

import com.example.bookingservice.dto.ShowtimeDTO;
import com.example.bookingservice.exception.InvalidBookingReferenceException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * booking-service's view of showtime-service, used to refuse bookings for a
 * showtime that doesn't exist or has already started.
 *
 * Like the user check, this fails closed: a showtime-service outage is a
 * 502 (or 503 with the breaker open), never "assume it's bookable".
 */
@Component
public class ShowtimeServiceClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public ShowtimeServiceClient(
            @LoadBalanced RestClient.Builder builder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.restClient = builder
                .baseUrl("http://SHOWTIME-SERVICE")
                .build();

        this.circuitBreaker = circuitBreakerFactory.create("showtime-service");
    }

    /** Throws InvalidBookingReferenceException (400) if there is no such showtime. */
    public ShowtimeDTO getShowtime(Integer showtimeId) {

        if (showtimeId == null) {
            throw new InvalidBookingReferenceException(
                    "showtimeId is required"
            );
        }

        return circuitBreaker.run(
                () -> doGetShowtime(showtimeId),
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    private ShowtimeDTO doGetShowtime(Integer showtimeId) {

        try {

            ShowtimeDTO showtime = restClient.get()
                    .uri("/api/showtimes/{id}", showtimeId)
                    .retrieve()
                    .body(ShowtimeDTO.class);

            if (showtime == null
                    || showtime.showDate() == null
                    || showtime.startTime() == null) {
                throw new IllegalStateException(
                        "showtime-service returned no start for showtime "
                                + showtimeId
                );
            }

            return showtime;

        } catch (RestClientResponseException ex) {

            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new InvalidBookingReferenceException(
                        "No showtime with ID: " + showtimeId
                );
            }

            throw ex;
        }
    }

    /**
     * Rethrows the original so the exception mapping still sees
     * InvalidBookingReferenceException (400) and RestClientException (502)
     * rather than a NoFallbackAvailableException wrapper.
     */
    private RuntimeException asRuntimeException(Throwable throwable) {

        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }

        return new IllegalStateException(
                "showtime-service call failed: " + throwable.getMessage(),
                throwable
        );
    }
}
