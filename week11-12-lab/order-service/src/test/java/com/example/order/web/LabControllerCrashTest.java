package com.example.order.web;

import com.example.order.kafka.CrashEventStore;
import com.example.order.kafka.OrderEvent;
import com.example.order.kafka.OrderEventPublisher;
import com.example.order.kafka.OrderSequenceTracker;
import com.example.order.payment.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LabControllerCrashTest {
    @Test
    void refusesToPublishWithoutExplicitConfirmation() {
        var publisher = mock(OrderEventPublisher.class);
        var controller = new LabController(publisher, mock(OrderSequenceTracker.class),
                mock(CrashEventStore.class), mock(PaymentGateway.class));
        var exception = assertThrows(ResponseStatusException.class,
                () -> controller.crashSend("raw", new LabController.CrashRequest("test-id", false)));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> controller.crashSend("raw", null));
        verifyNoInteractions(publisher);
    }

    @Test
    void storesExplicitCrashIntentInPublishedKafkaEvent() {
        var publisher = mock(OrderEventPublisher.class);
        var controller = new LabController(publisher, mock(OrderSequenceTracker.class),
                mock(CrashEventStore.class), mock(PaymentGateway.class));
        controller.crashSend("raw", new LabController.CrashRequest("test-id", true));
        var event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(publisher).publishCrash(eq("raw"), event.capture());
        assertEquals("crash-armed-raw", event.getValue().runId());
        assertEquals("test-id", event.getValue().eventId());
    }
}
