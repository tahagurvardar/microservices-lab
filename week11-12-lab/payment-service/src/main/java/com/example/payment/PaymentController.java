package com.example.payment;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final JdbcTemplate jdbc;

    public PaymentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/pay")
    public ResponseEntity<?> pay(
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(defaultValue = "0") int failRate,
            @RequestHeader(name = "Idempotency-Key", required = false) String key)
            throws InterruptedException {
        log.info("PAYMENT_REQUEST at={} key={} delayMs={} failRate={}", Instant.now(), key, delayMs, failRate);
        if (key == null || key.isBlank() || key.length() > 120 || delayMs < 0 || delayMs > 20000
                || failRate < 0 || failRate > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid Idempotency-Key, delayMs or failRate"));
        }
        // Once a key exists, always return its stored result, even when failRate is nonzero.
        // The unique INSERT below handles simultaneous first-time requests safely.
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_payment WHERE idempotency_key=?", Integer.class, key);
        if (existing != null && existing > 0) {
            return reusedPayment(key);
        }
        // Transient failures happen BEFORE payment is claimed; retries are therefore safe.
        if (ThreadLocalRandom.current().nextInt(100) < failRate) {
            log.warn("PAYMENT_SIMULATED_503 key={}", key);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "simulated 503"));
        }
        String paymentId = UUID.randomUUID().toString();
        // Unique database key is the concurrency-safe claim. It commits before artificial delay.
        int created = jdbc.update("INSERT INTO lab_payment(idempotency_key,payment_id,status) "
                + "VALUES (?,?,'PROCESSING') ON CONFLICT DO NOTHING", key, paymentId);
        if (created == 0) {
            return reusedPayment(key);
        }
        log.info("PAYMENT_CREATED key={} paymentId={}", key, paymentId);
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        jdbc.update("UPDATE lab_payment SET status='COMPLETED', completed_at=NOW() WHERE idempotency_key=?", key);
        log.info("PAYMENT_COMPLETED key={} paymentId={}", key, paymentId);
        return ResponseEntity.ok(Map.of("key", key, "paymentId", paymentId,
                "status", "COMPLETED", "reused", false));
    }

    private ResponseEntity<?> reusedPayment(String key) {
        var old = jdbc.queryForMap("SELECT payment_id,status FROM lab_payment WHERE idempotency_key=?", key);
        log.info("PAYMENT_REUSED key={} status={} paymentId={}", key, old.get("status"), old.get("payment_id"));
        return ResponseEntity.status("PROCESSING".equals(old.get("status"))
                        ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(Map.of("key", key, "paymentId", old.get("payment_id"),
                        "status", old.get("status"), "reused", true));
    }

    @GetMapping("/payments/count")
    public Map<String, Object> count(@RequestParam String orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_payment WHERE idempotency_key=?", Integer.class, orderId);
        return Map.of("orderId", orderId, "payments", count);
    }
}
