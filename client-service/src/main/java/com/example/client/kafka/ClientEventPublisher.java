package com.example.client.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ClientEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ClientEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public ClientEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.kafka.topic:client.events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public ClientEvent publish(ClientEventRequest request) {
        ClientEvent event = new ClientEvent(
                UUID.randomUUID().toString(), request.clientId().trim(),
                request.action().trim(), Instant.now());
        try {
            String json = objectMapper.writeValueAsString(event);
            // Wait for broker acknowledgment, not for either consumer to finish.
            kafkaTemplate.send(topic, event.clientId(), json).get(15, TimeUnit.SECONDS);
            log.info("KAFKA_PUBLISHED eventId={} clientId={} action={} topic={}",
                    event.eventId(), event.clientId(), event.action(), topic);
            return event;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialize client event", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kafka publish interrupted", failure);
        } catch (ExecutionException | TimeoutException | KafkaException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kafka could not confirm the event", failure);
        }
    }
}
