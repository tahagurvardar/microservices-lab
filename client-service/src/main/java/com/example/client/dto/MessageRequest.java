package com.example.client.dto;

public record MessageRequest(
        String author,
        String text
) {
}