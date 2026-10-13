package com.example.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    protected Message() {
    }

    public Message(
            String author,
            String text,
            int wordCount,
            LocalDateTime processedAt
    ) {
        this.author = author;
        this.text = text;
        this.wordCount = wordCount;
        this.processedAt = processedAt;
    }

    @PrePersist
    void onSave() {
        this.savedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public int getWordCount() {
        return wordCount;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}