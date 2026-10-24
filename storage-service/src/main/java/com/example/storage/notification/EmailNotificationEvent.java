package com.example.storage.notification;

public record EmailNotificationEvent(
        String eventId,
        Long messageId,
        String recipient,
        String subject,
        String body,
        String occurredAt
) {
}
