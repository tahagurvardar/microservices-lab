package com.example.storage.dto;

import java.time.LocalDateTime;

public record SaveMessageRequest(
        String author,
        String text,
        int wordCount,
        LocalDateTime processedAt
) {
}