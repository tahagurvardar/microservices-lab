package com.example.order.web;

import com.example.order.kafka.CrashEventStore;
import com.example.order.kafka.OrderEvent;
import com.example.order.kafka.OrderEventPublisher;
import com.example.order.kafka.OrderSequenceTracker;
import com.example.order.payment.PaymentGateway;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/lab")
public class LabController {
    private final OrderEventPublisher publisher;
    private final OrderSequenceTracker tracker;
    private final CrashEventStore store;
    private final PaymentGateway gateway;

    public LabController(OrderEventPublisher publisher, OrderSequenceTracker tracker,
            CrashEventStore store, PaymentGateway gateway) {
        this.publisher = publisher;
        this.tracker = tracker;
        this.store = store;
        this.gateway = gateway;
    }

    @PostMapping("/orders/keyed")
    public Map<String, Object> keyed() {
        return publisher.publishBatch(true);
    }

    @PostMapping("/orders/unkeyed")
    public Map<String, Object> unkeyed() {
        return publisher.publishBatch(false);
    }

    @GetMapping("/orders/results/{runId}")
    public Map<String, Object> results(@PathVariable String runId) {
        return tracker.observations(runId);
    }

    @PostMapping("/crash/send/{mode}")
    public ResponseEntity<Map<String, String>> crashSend(@PathVariable String mode,
            @RequestBody(required = false) CrashRequest request) {
        // Stateless explicit confirmation prevents stale one-shot arm state from
        // rejecting the request. The script itself also requires user to type YES.
        if (request == null || !Boolean.TRUE.equals(request.confirmCrash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Set confirmCrash:true in the JSON request to run the intentional lab crash");
        }
        String id = request.eventId() != null && !request.eventId().isBlank()
                ? request.eventId() : UUID.randomUUID().toString();
        // Crash intent travels with the Kafka record, so a replay is deterministic.
        OrderEvent event = new OrderEvent(id, "crash-armed-" + mode,
                "crash-customer", "CREATED", 1, mode, Instant.now().toString());
        publisher.publishCrash(mode, event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", id, "mode", mode));
    }

    @GetMapping("/crash/results/{eventId}")
    public Map<String, Object> crashResults(@PathVariable String eventId) {
        return store.results(eventId);
    }

    @PostMapping("/payments/{mode}")
    public Map<String, Object> pay(@PathVariable String mode,
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(defaultValue = "0") int failRate,
            @RequestParam(required = false) String orderId) {
        String id = orderId == null || orderId.isBlank() ? UUID.randomUUID().toString() : orderId;
        Supplier<String> action = switch (mode) {
            case "baseline" -> () -> gateway.baseline(id, delayMs, failRate);
            case "timeout" -> () -> gateway.timed(id, delayMs, failRate);
            case "retry" -> () -> gateway.retry(id, delayMs, failRate);
            case "circuit" -> () -> gateway.circuit(id, delayMs, failRate);
            default -> throw new IllegalArgumentException("mode must be baseline, timeout, retry or circuit");
        };
        long start = System.nanoTime();
        String result;
        try {
            result = action.get();
        } catch (Exception e) {
            result = "payment pending (" + e.getClass().getSimpleName() + ")";
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return Map.of("orderId", id, "mode", mode, "elapsedMs", elapsedMs, "result", result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    public record CrashRequest(String eventId, Boolean confirmCrash) { }
}
