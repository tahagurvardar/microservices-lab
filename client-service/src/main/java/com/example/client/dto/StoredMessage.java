package com.example.client.dto;

import java.time.LocalDateTime;

public record StoredMessage(
        Long id,
        String author,
        String text,
        int wordCount,
        LocalDateTime processedAt,
        LocalDateTime savedAt
) {
}