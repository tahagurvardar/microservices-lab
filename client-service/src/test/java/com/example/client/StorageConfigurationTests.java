package com.example.client;

import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StorageConfigurationTests {

    @Autowired
    private RetryRegistry retries;
    @Autowired
    private CircuitBreakerRegistry circuits;
    @Autowired
    private Retryer feignRetryer;

    @Test
    void productionResilienceConfigurationIsLoaded() {
        var retry = retries.retry("storage").getRetryConfig();
        assertThat(retry.getMaxAttempts()).isEqualTo(3);
        assertThat(retry.getIntervalBiFunction().apply(1, null)).isEqualTo(400L);
        assertThat(retry.getIntervalBiFunction().apply(2, null)).isEqualTo(800L);
        var circuit = circuits.circuitBreaker("storage").getCircuitBreakerConfig();
        assertThat(circuit.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(circuit.getSlidingWindowSize()).isEqualTo(5);
        assertThat(circuit.getMinimumNumberOfCalls()).isEqualTo(3);
        assertThat(circuit.getFailureRateThreshold()).isEqualTo(50);
        assertThat(circuit.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10000L);
        assertThat(circuit.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(feignRetryer).isSameAs(Retryer.NEVER_RETRY);
    }
}
