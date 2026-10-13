package com.example.storage.service;

import com.example.storage.dto.MessageResponse;
import com.example.storage.dto.SaveMessageRequest;
import com.example.storage.entity.Message;
import com.example.storage.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MessageResponse save(SaveMessageRequest request) {

        Message message = new Message(
                request.author(),
                request.text(),
                request.wordCount(),
                request.processedAt()
        );

        Message saved = repository.save(message);

        return MessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> findAll() {
        return repository
                .findAll()
                .stream()
                .map(MessageResponse::from)
                .toList();
    }
}