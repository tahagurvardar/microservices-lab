package com.example.order.kafka;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderSequenceTrackerTest {
    @Test
    void orderedEventsStayOrdered() {
        OrderSequenceTracker tracker = new OrderSequenceTracker();
        for (int seq = 1; seq <= 3; seq++) {
            assertFalse(tracker.record(event(seq, "run-1")));
        }
        assertEquals(0L, tracker.observations("run-1").get("outOfOrderCustomers"));
    }

    @Test
    void crossPartitionReorderingIsDetected() {
        OrderSequenceTracker tracker = new OrderSequenceTracker();
        assertTrue(tracker.record(event(2, "run-2")));
        assertTrue(tracker.record(event(1, "run-2")));
        assertTrue(tracker.record(event(3, "run-2")));
        assertEquals(1L, tracker.observations("run-2").get("outOfOrderCustomers"));
        assertFalse(OrderSequenceTracker.isOrdered(List.of(2, 1, 3)));
    }

    @Test
    void runsAreIndependent() {
        OrderSequenceTracker tracker = new OrderSequenceTracker();
        tracker.record(event(1, "a"));
        tracker.record(event(2, "b"));
        assertEquals(0L, tracker.observations("a").get("outOfOrderCustomers"));
        assertEquals(1L, tracker.observations("b").get("outOfOrderCustomers"));
    }

    private OrderEvent event(int seq, String runId) {
        return new OrderEvent("event-" + seq, runId, "customer-A", "CREATED", seq, "keyed", "2026-01-01T00:00:00Z");
    }
}
