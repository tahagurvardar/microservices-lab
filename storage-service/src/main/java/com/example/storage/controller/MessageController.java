package com.example.storage.controller;

import com.example.storage.dto.MessageResponse;
import com.example.storage.dto.SaveMessageRequest;
import com.example.storage.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/storage/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse save(
            @RequestBody SaveMessageRequest request
    ) {
        return messageService.save(request);
    }

    @GetMapping
    public List<MessageResponse> findAll() {
        return messageService.findAll();
    }
}