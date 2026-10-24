package com.example.storage.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
public class NotificationOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);

    private final NotificationOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public NotificationOutboxPublisher(NotificationOutboxRepository repository,
                                       RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(initialDelayString = "${notification.outbox.initial-delay-ms:5000}",
               fixedDelayString = "${notification.outbox.poll-ms:5000}")
    @Transactional
    public void publishPending() {
        for (NotificationOutbox pending : repository.findTop20BySentAtIsNullOrderByIdAsc()) {
            try {
                CorrelationData confirmation = new CorrelationData(pending.getEventId());
                rabbitTemplate.convertAndSend(
                        NotificationRabbitConfiguration.EXCHANGE,
                        NotificationRabbitConfiguration.ROUTING_KEY,
                        pending.getPayload(),
                        confirmation
                );
                CorrelationData.Confirm result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
                if (!result.isAck() || confirmation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ did not confirm routing of the notification");
                }
                pending.markSent();
                log.info("Notification published eventId={} messageOutboxId={}",
                        pending.getEventId(), pending.getId());
            } catch (Exception ex) {
                log.warn("Notification publish deferred; eventId={} reason={}",
                        pending.getEventId(), ex.toString());
                break;
            }
        }
    }
}
