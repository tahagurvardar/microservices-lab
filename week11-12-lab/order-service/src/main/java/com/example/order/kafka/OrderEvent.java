package com.example.order.kafka;

public record OrderEvent(
        String eventId,
        String runId,
        String customerId,
        String action,
        int seq,
        String mode,
        String occurredAt) {
}
