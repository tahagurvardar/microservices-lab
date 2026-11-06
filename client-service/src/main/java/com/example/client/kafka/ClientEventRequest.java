package com.example.client.kafka;

public record ClientEventRequest(String clientId, String action) {
}
