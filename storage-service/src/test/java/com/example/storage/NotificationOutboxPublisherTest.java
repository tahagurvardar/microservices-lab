package com.example.storage;

import com.example.storage.notification.NotificationOutbox;
import com.example.storage.notification.NotificationOutboxPublisher;
import com.example.storage.notification.NotificationOutboxRepository;
import com.example.storage.notification.NotificationRabbitConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationOutboxPublisherTest {
    private final NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificationOutboxPublisher publisher =
            new NotificationOutboxPublisher(repository, rabbitTemplate);

    @Test
    void marksOutboxSentOnlyAfterBrokerConfirmation() {
        NotificationOutbox event = new NotificationOutbox("evt-1", "{}");
        when(repository.findTop20BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData confirmation = invocation.getArgument(3);
            confirmation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(NotificationRabbitConfiguration.EXCHANGE),
                eq(NotificationRabbitConfiguration.ROUTING_KEY), eq("{}"), any(CorrelationData.class));

        publisher.publishPending();
        assertThat(event.getSentAt()).isNotNull();
        verify(rabbitTemplate).convertAndSend(eq(NotificationRabbitConfiguration.EXCHANGE),
                eq(NotificationRabbitConfiguration.ROUTING_KEY), eq("{}"), any(CorrelationData.class));
    }

    @Test
    void leavesEventPendingAfterPublishFailure() {
        NotificationOutbox event = new NotificationOutbox("evt-2", "{}");
        when(repository.findTop20BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(rabbitTemplate).convertAndSend(
                        eq(NotificationRabbitConfiguration.EXCHANGE),
                        eq(NotificationRabbitConfiguration.ROUTING_KEY), eq("{}"), any(CorrelationData.class));

        publisher.publishPending();
        assertThat(event.getSentAt()).isNull();
    }
}
