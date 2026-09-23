package com.example.paymentservice.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * How long we are willing to wait on another service before giving up.
     *
     * A RestClient with no timeout waits forever. One hung dependency then
     * holds a request thread per caller until the pool is exhausted, and a
     * service that is merely slow takes down every service that calls it.
     * These two values are the difference between a degraded dependency and
     * a cascading outage.
     *
     * Read is the one that matters: the connect phase either succeeds fast
     * or fails fast, but a socket that connects and then goes quiet is
     * exactly the failure that hangs forever.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Plain builder. Marked @Primary so infrastructure that injects
     * RestClient.Builder by type (Eureka's own HTTP client) gets this one
     * and does NOT go through Spring Cloud LoadBalancer.
     *
     * Deliberately left untimed — Eureka manages its own client and its
     * polling has different timing needs from a request-path call.
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Load-balanced builder for service-to-service calls. Inject it with
     * the @LoadBalanced qualifier — by type alone you get the @Primary
     * plain builder above and lb service ids will not resolve.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return timedBuilder();
    }

    /**
     * For the Bakong Open API — the one outbound call that leaves the
     * cluster. It must NOT be @LoadBalanced: that would try to resolve
     * api-bakong.nbc.gov.kh as a Eureka service id and fail. It must not be
     * the untimed @Primary builder either, because a hung Bakong call would
     * pin a request thread per polling browser.
     *
     * Inject with @Qualifier(BAKONG_REST_CLIENT_BUILDER).
     */
    @Bean(BAKONG_REST_CLIENT_BUILDER)
    public RestClient.Builder bakongRestClientBuilder() {
        return timedBuilder();
    }

    public static final String BAKONG_REST_CLIENT_BUILDER =
            "bakongRestClientBuilder";

    private static RestClient.Builder timedBuilder() {

        HttpClientSettings settings =
                HttpClientSettings.defaults()
                        .withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(
                        ClientHttpRequestFactoryBuilder.detect().build(settings)
                );
    }
}
