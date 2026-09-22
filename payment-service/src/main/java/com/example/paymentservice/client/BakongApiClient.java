package com.example.paymentservice.client;

import com.example.paymentservice.config.BakongProperties;
import com.example.paymentservice.config.RestClientConfig;
import com.example.paymentservice.dto.BakongCheckResponseDTO;
import com.example.paymentservice.dto.BakongTransactionDTO;
import com.example.paymentservice.exception.BakongApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * payment-service's view of the Bakong Open API (National Bank of Cambodia).
 *
 * Bakong does not call us back when a KHQR is paid, so settlement is found
 * by asking: "has a transaction for the QR with this md5 completed?". That
 * question is asked by the checkout screen's polling and by
 * BakongPaymentSweeper.
 *
 * "Not paid yet" is an ordinary answer and returns Optional.empty() — it
 * must not count as a breaker failure, or a few customers taking their time
 * to scan would open the circuit. Only a real error (bad token, transport
 * failure) throws.
 */
@Component
public class BakongApiClient {

    /** Bakong's errorCode for "no transaction with that md5". */
    private static final int TRANSACTION_NOT_FOUND = 1;

    /** Bakong's errorCode for "the transaction exists but failed". */
    private static final int TRANSACTION_FAILED = 3;

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public BakongApiClient(
            @Qualifier(RestClientConfig.BAKONG_REST_CLIENT_BUILDER)
            RestClient.Builder builder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            BakongProperties properties
    ) {
        this.restClient = builder
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.apiToken()
                )
                .build();

        this.circuitBreaker = circuitBreakerFactory.create("bakong");
    }

    /**
     * Looks up the completed transaction for a KHQR by its md5.
     *
     * @return the transaction if it completed, empty if nobody has paid it
     *         (or the attempt failed at the bank, which is also "not paid")
     */
    public Optional<BakongTransactionDTO> checkTransactionByMd5(String md5) {

        return circuitBreaker.run(
                () -> doCheck(md5),
                throwable -> {
                    throw asRuntimeException(throwable);
                }
        );
    }

    private Optional<BakongTransactionDTO> doCheck(String md5) {

        BakongCheckResponseDTO response;

        try {
            response = restClient.post()
                    .uri("/v1/check_transaction_by_md5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("md5", md5))
                    .retrieve()
                    .body(BakongCheckResponseDTO.class);
        } catch (HttpClientErrorException.Unauthorized ex) {
            // HTTP 401 with errorCode 6. Tokens expire about every 90 days,
            // so this is the failure an operator will actually meet.
            throw new BakongApiException(
                    "Bakong rejected the API token (invalid or expired) — "
                            + "renew BAKONG_API_TOKEN"
            );
        }

        if (response == null || response.responseCode() == null) {
            throw new BakongApiException("Empty response from Bakong");
        }

        if (response.responseCode() == 0) {

            if (response.data() == null) {
                throw new BakongApiException(
                        "Bakong reported success with no transaction data"
                );
            }

            return Optional.of(response.data());
        }

        Integer errorCode = response.errorCode();

        if (errorCode != null
                && (errorCode == TRANSACTION_NOT_FOUND
                || errorCode == TRANSACTION_FAILED)) {
            return Optional.empty();
        }

        throw new BakongApiException(
                "Bakong check failed (errorCode " + errorCode + "): "
                        + response.responseMessage()
        );
    }

    /**
     * Rethrows the original failure rather than letting Spring Cloud wrap it
     * in NoFallbackAvailableException, so the 502/503 mapping still applies.
     */
    private RuntimeException asRuntimeException(Throwable throwable) {

        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }

        return new IllegalStateException(
                "Bakong call failed: " + throwable.getMessage(),
                throwable
        );
    }
}
