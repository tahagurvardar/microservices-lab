package com.example.client.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ClientEventPublisherTest {
    @SuppressWarnings("unchecked")
    @Test
    void publishesJsonEventAndWaitsForBrokerAck() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CompletableFuture<SendResult<String, String>> acknowledged =
                CompletableFuture.completedFuture(null);
        when(kafka.send(eq("client.events"), eq("customer-1"), anyString()))
                .thenReturn(acknowledged);

        ClientEventPublisher publisher = new ClientEventPublisher(kafka, mapper, "client.events");
        ClientEvent event = publisher.publish(new ClientEventRequest(" customer-1 ", "REGISTERED"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("client.events"), eq("customer-1"), payload.capture());
        JsonNode json = mapper.readTree(payload.getValue());
        assertThat(event.eventId()).isNotBlank();
        assertThat(json.path("eventId").asText()).isEqualTo(event.eventId());
        assertThat(json.path("clientId").asText()).isEqualTo("customer-1");
        assertThat(json.path("action").asText()).isEqualTo("REGISTERED");
        assertThat(json.path("occurredAt").asText()).isNotBlank();
    }
}
