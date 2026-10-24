package com.example.storage.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_outbox",
        indexes = @Index(name = "idx_notification_outbox_sent", columnList = "sent_at,id"))
public class NotificationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected NotificationOutbox() {
    }

    public NotificationOutbox(String eventId, String payload) {
        this.eventId = eventId;
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void markSent() {
        this.sentAt = LocalDateTime.now();
    }
}
