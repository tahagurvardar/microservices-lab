package com.example.order.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);
    private final RestClient baselineClient;
    private final RestClient timedClient;
    private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    public PaymentGateway(@Value("${payment.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory unlimited = new SimpleClientHttpRequestFactory();
        unlimited.setConnectTimeout(0);
        unlimited.setReadTimeout(0);
        baselineClient = RestClient.builder().baseUrl(baseUrl).requestFactory(unlimited).build();

        SimpleClientHttpRequestFactory bounded = new SimpleClientHttpRequestFactory();
        bounded.setConnectTimeout(1000);
        bounded.setReadTimeout(2000);
        timedClient = RestClient.builder().baseUrl(baseUrl).requestFactory(bounded).build();
    }

    public String baseline(String orderId, long delayMs, int failRate) {
        return call(baselineClient, "baseline", orderId, delayMs, failRate);
    }

    public String timed(String orderId, long delayMs, int failRate) {
        return call(timedClient, "timeout", orderId, delayMs, failRate);
    }

    @Retry(name = "paymentRetry")
    public String retry(String orderId, long delayMs, int failRate) {
        return call(timedClient, "retry", orderId, delayMs, failRate);
    }

    @CircuitBreaker(name = "paymentCircuit", fallbackMethod = "circuitFallback")
    public String circuit(String orderId, long delayMs, int failRate) {
        return call(timedClient, "circuit", orderId, delayMs, failRate);
    }

    public String circuitFallback(String orderId, long delayMs, int failRate, Throwable failure) {
        log.warn("PAYMENT_CIRCUIT_FALLBACK at={} orderId={} error={}",
                Instant.now(), orderId, failure.getClass().getSimpleName());
        return "payment pending (circuit fallback: " + failure.getClass().getSimpleName() + ")";
    }

    private String call(RestClient client, String mode, String orderId, long delayMs, int failRate) {
        int attempt = attempts.computeIfAbsent(mode + ":" + orderId, k -> new AtomicInteger())
                .incrementAndGet();
        log.info("PAYMENT_ATTEMPT at={} mode={} orderId={} attempt={} delayMs={} failRate={}",
                Instant.now(), mode, orderId, attempt, delayMs, failRate);
        try {
            return client.post()
                    .uri(uri -> uri.path("/pay").queryParam("delayMs", delayMs)
                            .queryParam("failRate", failRate).build())
                    .header("Idempotency-Key", orderId)
                    .retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("PAYMENT_HTTP_ERROR at={} mode={} orderId={} attempt={} status={}",
                    Instant.now(), mode, orderId, attempt, e.getStatusCode().value());
            if (e.getStatusCode().value() == 400) {
                throw new BadRequestPaymentException("payment-service returned 400; do not retry");
            }
            throw e;
        }
    }
}
