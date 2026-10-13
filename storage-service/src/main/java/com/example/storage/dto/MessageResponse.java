package com.example.storage.dto;

import com.example.storage.entity.Message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        String author,
        String text,
        int wordCount,
        LocalDateTime processedAt,
        LocalDateTime savedAt
) {

    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getAuthor(),
                m.getText(),
                m.getWordCount(),
                m.getProcessedAt(),
                m.getSavedAt()
        );
    }
}