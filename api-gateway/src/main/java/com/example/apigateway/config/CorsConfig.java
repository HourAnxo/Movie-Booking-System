package com.example.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS for browser clients, at the gateway and nowhere else.
 *
 * Two things here are easy to get wrong and both produce failures that look
 * nothing like CORS:
 *
 * 1. ORDER. The browser sends a preflight OPTIONS with no Authorization
 *    header. JwtAuthenticationFilter would answer that 401, the browser
 *    would report a CORS error, and the real request would never be sent.
 *    HIGHEST_PRECEDENCE puts CorsFilter first, where it answers the
 *    preflight itself and never reaches the JWT check.
 *
 * 2. ONE PLACE ONLY. Adding CORS to the downstream services as well makes
 *    them emit their own Access-Control-Allow-Origin, and the browser
 *    rejects a response carrying two of them. The gateway is the only
 *    origin a browser ever talks to, so it is the only place this belongs.
 *
 * None of this affects curl, which ignores CORS entirely — which is why the
 * API can pass every script in scripts/ and still be unusable from a page.
 */
@Configuration
public class CorsConfig {

    /**
     * Explicit origins, not "*": allowCredentials cannot be combined with a
     * wildcard, and listing them keeps an arbitrary site from driving the
     * API with a logged-in user's browser. Override for a deployed frontend
     * with CORS_ALLOWED_ORIGINS.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
            List<String> allowedOrigins
    ) {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept")
        );

        // The browser can only read headers named here. Without it a client
        // cannot see anything the gateway adds to a response.
        config.setExposedHeaders(List.of("Authorization"));

        config.setAllowCredentials(true);

        // Cache the preflight so the browser is not asking again on every
        // request.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));

        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}
