package com.example.audit.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClientEventListener {
    private static final Logger log = LoggerFactory.getLogger(ClientEventListener.class);
    private final ObjectMapper mapper;

    public ClientEventListener(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${app.kafka.group-id}")
    public void receive(String payload) {
        try {
            JsonNode event = mapper.readTree(payload);
            String eventId = required(event, "eventId");
            String clientId = required(event, "clientId");
            String action = required(event, "action");
            log.info("KAFKA_AUDIT_RECEIVED eventId={} clientId={} action={}",
                    eventId, clientId, action);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Malformed client event", failure);
        }
    }

    private static String required(JsonNode event, String field) {
        String value = event.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + field);
        }
        return value;
    }
}
