package com.example.authservice.client;

import com.example.authservice.dto.CreateUserRequestDTO;
import com.example.authservice.dto.UserProfileDTO;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * auth-service's view of user-service.
 *
 * AuthServiceImpl.register is @Transactional around createUser, so a
 * failure here rolls the credential row back rather than leaving auth_db
 * and user_db out of step. That is why there is no fallback: swallowing the
 * error would commit a login with no profile behind it.
 *
 * An open breaker therefore fails registration outright, which is the
 * intended behaviour — a fast, honest refusal beats a half-created account.
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
     * Creates the user's profile row and returns the id user-service
     * assigned it.
     *
     * The id is the whole point: it is what Booking.userId refers to, and
     * discarding it (as this used to) left an authenticated caller with no
     * way to say who a booking was for.
     */
    public Integer createUser(CreateUserRequestDTO request) {

        return circuitBreaker.run(
                () -> {
                    UserProfileDTO created = restClient.post()
                            .uri("/api/users")
                            .body(request)
                            .retrieve()
                            .body(UserProfileDTO.class);

                    return created == null ? null : created.userId();
                },
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    /**
     * Finds an existing profile id by email, for accounts registered before
     * the link was stored. Returns null when there is no such profile —
     * this runs during login, and a missing profile must not stop someone
     * signing in.
     */
    public Integer findProfileIdByEmail(String email) {

        return circuitBreaker.run(
                () -> {
                    try {
                        UserProfileDTO found = restClient.get()
                                .uri("/api/users/email/{email}", email)
                                .retrieve()
                                .body(UserProfileDTO.class);

                        return found == null ? null : found.userId();

                    } catch (RestClientResponseException ex) {

                        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                            return null;
                        }

                        throw ex;
                    }
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
                "user-service call failed: " + throwable.getMessage(),
                throwable
        );
    }
}
