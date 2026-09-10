package com.example.adminservice.service;

import com.example.adminservice.dto.UserDTO;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    /**
     * Must be the @LoadBalanced builder. Injecting RestClient.Builder by
     * type alone resolves to the @Primary plain builder in
     * {@code RestClientConfig}, and the http://USER-SERVICE service id then
     * has no resolver behind it.
     */
    public AdminServiceImpl(
            @LoadBalanced RestClient.Builder restClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.restClient = restClientBuilder.build();
        this.circuitBreaker = circuitBreakerFactory.create("user-service");
    }

    /**
     * admin-service holds no data of its own, so this list is entirely
     * user-service's. An empty-list fallback would be actively harmful here
     * — an admin screen showing "no users" is indistinguishable from a real
     * empty system, and someone would act on it. Failing is the honest
     * answer.
     */
    @Override
    public List<UserDTO> getAllUsers() {

        return circuitBreaker.run(
                () -> restClient.get()
                        .uri("http://USER-SERVICE/api/users")
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<UserDTO>>() {}),
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
