package com.example.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ClientEventListenerTest {
    @Test
    void readsAndLogsClientEvent(CapturedOutput output) {
        ClientEventListener listener = new ClientEventListener(new ObjectMapper());
        listener.receive("{\"eventId\":\"evt-42\",\"clientId\":\"customer-1\",\"action\":\"REGISTERED\"}");
        assertThat(output.toString()).contains("KAFKA_AUDIT_RECEIVED", "evt-42", "customer-1");
    }

    @Test
    void missingEventIdIsRejected() {
        ClientEventListener listener = new ClientEventListener(new ObjectMapper());
        assertThatThrownBy(() -> listener.receive("{\"clientId\":\"customer-1\",\"action\":\"REGISTERED\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }
}
