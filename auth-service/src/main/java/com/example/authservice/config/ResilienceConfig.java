package com.example.authservice.config;


import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/**
 * Circuit breaker settings for this service's outbound calls.
 *
 * The breaker exists to stop us hammering a dependency that is already
 * down, and to fail in milliseconds instead of waiting out the timeout on
 * every single request. It does NOT change what a failure means: there are
 * no fallbacks here that invent a result. A call that cannot be made still
 * fails — it just fails immediately.
 *
 * The one subtle part is {@link CircuitBreakerConfig.Builder#ignoreExceptions}.
 * A 4xx means the dependency answered perfectly well and rejected OUR
 * request; counting it as a failure would let ordinary business outcomes
 * trip the breaker and take the dependency out for everybody.
 */
@Configuration
public class ResilienceConfig {

    // Opens once half the calls in the window fail, but only after enough
    // calls to be meaningful — without a minimum, the first failed call in
    // a quiet period is a 100% failure rate and opens the circuit.
    private static final int SLIDING_WINDOW_SIZE = 20;
    private static final int MINIMUM_CALLS = 10;
    private static final float FAILURE_RATE_THRESHOLD = 50.0f;

    // How long to stay open before letting a few probes through.
    private static final Duration WAIT_IN_OPEN_STATE = Duration.ofSeconds(10);
    private static final int PROBES_WHEN_HALF_OPEN = 3;

    // Deliberately longer than the RestClient read timeout in
    // RestClientConfig, so the HTTP timeout is what fires and the logs name
    // the real cause rather than a generic time-limiter breach.
    private static final Duration TIME_LIMIT = Duration.ofSeconds(10);

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerConfig() {

        CircuitBreakerConfig circuitBreakerConfig =
                CircuitBreakerConfig.custom()
                        .slidingWindowType(
                                CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                        )
                        .slidingWindowSize(SLIDING_WINDOW_SIZE)
                        .minimumNumberOfCalls(MINIMUM_CALLS)
                        .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                        .waitDurationInOpenState(WAIT_IN_OPEN_STATE)
                        .permittedNumberOfCallsInHalfOpenState(PROBES_WHEN_HALF_OPEN)
                        .ignoreExceptions(
                                HttpClientErrorException.class
                        )
                        .build();

        TimeLimiterConfig timeLimiterConfig =
                TimeLimiterConfig.custom()
                        .timeoutDuration(TIME_LIMIT)
                        .build();

        return factory -> factory.configureDefault(id ->
                new Resilience4JConfigBuilder(id)
                        .circuitBreakerConfig(circuitBreakerConfig)
                        .timeLimiterConfig(timeLimiterConfig)
                        .build()
        );
    }
}
