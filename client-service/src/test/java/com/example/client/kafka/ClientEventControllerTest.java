package com.example.client.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientEventControllerTest {
    @Test
    void validRequestReturnsAcceptedEvent() throws Exception {
        ClientEventPublisher publisher = mock(ClientEventPublisher.class);
        when(publisher.publish(any(ClientEventRequest.class))).thenReturn(
                new ClientEvent("event-123", "customer-1", "REGISTERED", Instant.parse("2026-11-06T12:00:00Z")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientEventController(publisher)).build();

        mvc.perform(post("/api/client/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"customer-1\",\"action\":\"REGISTERED\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("event-123"))
                .andExpect(jsonPath("$.clientId").value("customer-1"));
    }

    @Test
    void blankClientIdReturnsBadRequestWithoutPublishing() throws Exception {
        ClientEventPublisher publisher = mock(ClientEventPublisher.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientEventController(publisher)).build();
        mvc.perform(post("/api/client/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\" \",\"action\":\"REGISTERED\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(publisher);
    }
}
