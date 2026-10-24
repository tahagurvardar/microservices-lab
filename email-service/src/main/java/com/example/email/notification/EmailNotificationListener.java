package com.example.email.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EmailNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationListener(ObjectMapper objectMapper,
                                     JavaMailSender mailSender,
                                     @Value("${notification.sender}") String from) {
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.from = from;
    }

    @RabbitListener(queues = NotificationRabbitConfiguration.QUEUE)
    public void handle(String payload) throws IOException {
        EmailNotificationEvent event = objectMapper.readValue(payload, EmailNotificationEvent.class);
        if (event.eventId() == null || event.eventId().isBlank()
                || event.messageId() == null
                || event.recipient() == null || !event.recipient().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || event.subject() == null || event.subject().isBlank()
                || event.body() == null || event.body().isBlank()) {
            throw new IllegalArgumentException("Invalid notification event");
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(event.recipient());
        mail.setSubject(event.subject());
        mail.setText(event.body());
        mailSender.send(mail);
        log.info("Email sent eventId={} messageId={}", event.eventId(), event.messageId());
    }
}
