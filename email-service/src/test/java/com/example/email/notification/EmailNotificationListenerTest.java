package com.example.email.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailNotificationListenerTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final EmailNotificationListener listener =
            new EmailNotificationListener(new ObjectMapper(), sender, "no-reply@example.test");

    @Test
    void sendsEmailForValidNotification() throws IOException {
        listener.handle("""
                {"eventId":"evt-1","messageId":42,"recipient":"ada@example.test",
                 "subject":"New message","body":"Hello from storage",
                 "occurredAt":"2026-10-24T12:00:00Z"}
                """);
        org.mockito.ArgumentCaptor<SimpleMailMessage> captured =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertThat(captured.getValue().getTo()).containsExactly("ada@example.test");
        assertThat(captured.getValue().getText()).isEqualTo("Hello from storage");
    }

    @Test
    void rejectsInvalidRecipientWithoutSending() {
        assertThatThrownBy(() -> listener.handle("""
                {"eventId":"evt-2","messageId":43,"recipient":"invalid",
                 "subject":"Hi","body":"Hello"}
                """))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void propagatesSmtpFailureForListenerRetry() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> listener.handle("""
                {"eventId":"evt-3","messageId":44,"recipient":"ada@example.test",
                 "subject":"Hi","body":"Hello"}
                """))
                .isInstanceOf(MailSendException.class);
        verify(sender).send(any(SimpleMailMessage.class));
    }
}
