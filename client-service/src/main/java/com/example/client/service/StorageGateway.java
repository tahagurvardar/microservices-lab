package com.example.client.service;

import com.example.client.dto.ProcessedMessage;
import com.example.client.dto.StoredMessage;
import com.example.client.feign.StorageClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class StorageGateway {

    private static final Logger log = LoggerFactory.getLogger(StorageGateway.class);

    private final StorageClient client;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public StorageGateway(StorageClient client, RetryRegistry retries,
                          CircuitBreakerRegistry circuitBreakers) {
        this.client = client;
        this.retry = retries.retry("storage");
        this.circuitBreaker = circuitBreakers.circuitBreaker("storage");
        retry.getEventPublisher().onRetry(event -> log.warn(
                "Storage retry={} reason={}", event.getNumberOfRetryAttempts(),
                event.getLastThrowable().getClass().getSimpleName()));
        circuitBreaker.getEventPublisher().onStateTransition(event -> log.info(
                "Storage circuit transition={}", event.getStateTransition()));
        circuitBreaker.getEventPublisher().onCallNotPermitted(event -> log.warn(
                "Storage circuit rejected call"));
    }

    public Map<String, Object> ping() {
        return execute("ping", client::ping, true);
    }

    public String greet(String name) {
        return execute("greet", () -> client.greet(name), true);
    }

    public List<StoredMessage> getAllMessages() {
        return execute("getMessages", client::getAllMessages, true);
    }

    public StoredMessage saveMessage(ProcessedMessage message) {
        return execute("saveMessage", () -> client.saveMessage(message), false);
    }

    public String simulateError(int status) {
        return execute("simulateError", () -> client.simulateError(status), true);
    }

    private <T> T execute(String operation, Supplier<T> call, boolean retryable) {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<T> loggedCall = () -> {
            log.info("Storage call operation={} attempt={}", operation, attempts.incrementAndGet());
            return call.get();
        };
        Supplier<T> protectedCall = retryable
                ? Retry.decorateSupplier(retry, loggedCall) : loggedCall;
        return circuitBreaker.executeSupplier(protectedCall);
    }
}
