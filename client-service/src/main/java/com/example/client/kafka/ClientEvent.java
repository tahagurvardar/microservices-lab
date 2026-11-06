package com.example.client.kafka;

import java.time.Instant;

public record ClientEvent(
        String eventId,
        String clientId,
        String action,
        Instant occurredAt
) {
}
