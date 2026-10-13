package com.example.client.service;

import com.example.client.dto.MessageRequest;
import com.example.client.dto.ProcessedMessage;
import com.example.client.dto.StoredMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageProcessingService {

    private final StorageGateway storageClient;

    public MessageProcessingService(
            StorageGateway storageClient
    ) {
        this.storageClient = storageClient;
    }

    public StoredMessage processAndStore(
            MessageRequest request
    ) {

        validate(request);

        String author =
                capitalize(request.author().trim());

        String text =
                request.text()
                        .trim()
                        .replaceAll("\\s+", " ");

        int wordCount =
                text.split(" ").length;

        ProcessedMessage processed =
                new ProcessedMessage(
                        author,
                        text,
                        wordCount,
                        LocalDateTime.now()
                );

        return storageClient.saveMessage(processed);
    }

    public List<StoredMessage> getAllMessages() {
        return storageClient.getAllMessages();
    }

    private void validate(MessageRequest request) {

        if (
                request == null ||
                isBlank(request.author()) ||
                isBlank(request.text())
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both 'author' and 'text' are required"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase()
                + value.substring(1).toLowerCase();
    }
}
