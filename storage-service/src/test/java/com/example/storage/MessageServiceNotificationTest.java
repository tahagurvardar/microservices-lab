package com.example.storage;

import com.example.storage.dto.SaveMessageRequest;
import com.example.storage.entity.Message;
import com.example.storage.notification.NotificationOutbox;
import com.example.storage.notification.NotificationOutboxRepository;
import com.example.storage.repository.MessageRepository;
import com.example.storage.service.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageServiceNotificationTest {
    private final MessageRepository messages = mock(MessageRepository.class);
    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageService service = new MessageService(
            messages, outbox, objectMapper, "ada@example.test");

    @Test
    void savesMessageAndOutboxEventTogether() throws Exception {
        when(messages.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
        var response = service.save(new SaveMessageRequest(
                "Ada", "hello world", 2, LocalDateTime.of(2026, 10, 24, 12, 0)));

        assertThat(response.id()).isEqualTo(42L);
        ArgumentCaptor<NotificationOutbox> captured = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outbox).save(captured.capture());
        JsonNode event = objectMapper.readTree(captured.getValue().getPayload());
        assertThat(event.path("messageId").asLong()).isEqualTo(42L);
        assertThat(event.path("recipient").asText()).isEqualTo("ada@example.test");
        assertThat(event.path("body").asText()).contains("hello world");
        assertThat(event.path("eventId").asText()).isNotBlank();
    }

    @Test
    void readOperationsDoNotPublishNotifications() {
        when(messages.findAll()).thenReturn(List.of());
        assertThat(service.findAll()).isEmpty();
        verifyNoInteractions(outbox);
    }
}
