package com.example.client.controller;

import com.example.client.dto.MessageRequest;
import com.example.client.dto.StoredMessage;
import com.example.client.service.MessageProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageProcessingService processingService;

    public MessageController(
            MessageProcessingService processingService
    ) {
        this.processingService = processingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoredMessage create(
            @RequestBody MessageRequest request
    ) {
        return processingService.processAndStore(request);
    }

    @GetMapping
    public List<StoredMessage> getAll() {
        return processingService.getAllMessages();
    }
}