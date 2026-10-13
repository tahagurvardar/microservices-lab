package com.example.client.dto;

import java.time.LocalDateTime;

public record ProcessedMessage(
        String author,
        String text,
        int wordCount,
        LocalDateTime processedAt
) {
}