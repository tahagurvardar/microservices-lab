package com.example.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(OrderKafkaListener.class);
    private final ObjectMapper mapper;
    private final OrderSequenceTracker tracker;
    private final CrashEventStore eventStore;

    public OrderKafkaListener(ObjectMapper mapper, OrderSequenceTracker tracker,
            CrashEventStore eventStore) {
        this.mapper = mapper;
        this.tracker = tracker;
        this.eventStore = eventStore;
    }

    @KafkaListener(topics = "orders", groupId = "lab-order-demo", concurrency = "3")
    public void receiveOrder(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        // Task 1 uses the console producer with plain-text values (not JSON).
        // Acknowledge those demonstrations so they cannot poison the Java listener.
        if (!record.value().stripLeading().startsWith("{")) {
            log.info("ORDER_CONSOLE_OBSERVED partition={} offset={} key={} value={}",
                    record.partition(), record.offset(), record.key(), record.value());
            ack.acknowledge();
            return;
        }
        OrderEvent event = mapper.readValue(record.value(), OrderEvent.class);
        // Intentionally delay a keyless CREATED event to illustrate cross-partition reordering.
        // This is a demonstration aid, not a claim Kafka always reorders keyless messages.
        if ("unkeyed".equals(event.mode()) && event.seq() == 1) {
            Thread.sleep(900);
        }
        // Persist the event before acknowledging its Kafka offset.
        eventStore.save(event, record.partition(), record.offset(), false);
        boolean outOfOrder = tracker.record(event);
        log.info("ORDER_CONSUMED mode={} runId={} partition={} offset={} key={} customer={} seq={} action={}",
                event.mode(), event.runId(), record.partition(), record.offset(), record.key(),
                event.customerId(), event.seq(), event.action());
        if (outOfOrder) {
            log.warn("ORDER_OUT_OF_ORDER runId={} customer={} seq={} partition={}",
                    event.runId(), event.customerId(), event.seq(), record.partition());
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "orders-crash-raw", groupId = "lab-crash-raw", concurrency = "1")
    public void receiveRaw(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        receiveCrash(record, ack, "raw");
    }

    @KafkaListener(topics = "orders-crash-safe", groupId = "lab-crash-safe", concurrency = "1")
    public void receiveSafe(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        receiveCrash(record, ack, "safe");
    }

    private void receiveCrash(ConsumerRecord<String, String> record, Acknowledgment ack, String mode)
            throws Exception {
        OrderEvent event = mapper.readValue(record.value(), OrderEvent.class);
        if (!mode.equals(event.mode())) {
            throw new IllegalArgumentException("Kafka topic/mode mismatch");
        }
        // The explicit crash request is embedded in the Kafka record. On replay
        // it is still present; CrashEventStore's DB marker guarantees one halt.
        boolean armed = ("crash-armed-" + mode).equals(event.runId());
        log.info("CRASH_EVENT_RECEIVED mode={} eventId={} partition={} offset={} armed={}",
                mode, event.eventId(), record.partition(), record.offset(), armed);
        if (armed) {
            Thread.sleep(1200); // Allow the HTTP publish response to complete.
        }
        CrashEventStore.SaveResult result = eventStore.save(event, record.partition(), record.offset(), armed);
        log.info("CRASH_EVENT_PROCESSED mode={} eventId={} partition={} offset={} duplicate={} committedToDb=true",
                mode, event.eventId(), record.partition(), record.offset(), result.duplicate());
        if (result.crash()) {
            log.error("LAB_INTENTIONAL_HALT mode={} eventId={} partition={} offset={} (DB COMMITTED, KAFKA NOT ACKED)",
                    mode, event.eventId(), record.partition(), record.offset());
            System.out.flush();
            System.err.flush();
            Runtime.getRuntime().halt(1); // Explicitly requested by the assignment; never enable in production.
        }
        ack.acknowledge();
        log.info("CRASH_EVENT_ACK mode={} eventId={} partition={} offset={}",
                mode, event.eventId(), record.partition(), record.offset());
    }
}
