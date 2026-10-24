package com.example.storage.service;

import com.example.storage.dto.MessageResponse;
import com.example.storage.dto.SaveMessageRequest;
import com.example.storage.entity.Message;
import com.example.storage.notification.EmailNotificationEvent;
import com.example.storage.notification.NotificationOutbox;
import com.example.storage.notification.NotificationOutboxRepository;
import com.example.storage.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {
    private final MessageRepository repository;
    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String notificationRecipient;

    public MessageService(MessageRepository repository,
                          NotificationOutboxRepository outboxRepository,
                          ObjectMapper objectMapper,
                          @Value("${notification.recipient}") String notificationRecipient) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.notificationRecipient = notificationRecipient;
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

        EmailNotificationEvent event = new EmailNotificationEvent(
                UUID.randomUUID().toString(),
                saved.getId(),
                notificationRecipient,
                "New message stored (#" + saved.getId() + ")",
                "Author: " + saved.getAuthor() + "\n" +
                "Message: " + saved.getText() + "\n" +
                "Word count: " + saved.getWordCount(),
                Instant.now().toString()
        );
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(new NotificationOutbox(event.eventId(), payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize email notification", ex);
        }
        return MessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> findAll() {
        return repository.findAll().stream().map(MessageResponse::from).toList();
    }
}
