package com.example.order.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final List<String> ACTIONS = List.of("CREATED", "PAID", "SHIPPED");
    private final KafkaTemplate<String, String> keyed;
    private final KafkaTemplate<String, String> roundRobin;
    private final ObjectMapper mapper;

    public OrderEventPublisher(
            @Qualifier("keyedKafkaTemplate") KafkaTemplate<String, String> keyed,
            @Qualifier("roundRobinKafkaTemplate") KafkaTemplate<String, String> roundRobin,
            ObjectMapper mapper) {
        this.keyed = keyed;
        this.roundRobin = roundRobin;
        this.mapper = mapper;
    }

    public Map<String, Object> publishBatch(boolean withKey) {
        String runId = UUID.randomUUID().toString();
        for (int customer = 1; customer <= 10; customer++) {
            String customerId = "customer-" + customer;
            for (int seq = 1; seq <= 3; seq++) {
                OrderEvent event = new OrderEvent(UUID.randomUUID().toString(), runId,
                        customerId, ACTIONS.get(seq - 1), seq,
                        withKey ? "keyed" : "unkeyed", Instant.now().toString());
                publish("orders", withKey ? customerId : null, event, !withKey);
            }
        }
        return Map.of("runId", runId, "mode", withKey ? "keyed" : "unkeyed",
                "eventsPublished", 30, "customers", 10);
    }

    public void publishCrash(String mode, OrderEvent event) {
        if (!mode.equals("raw") && !mode.equals("safe")) {
            throw new IllegalArgumentException("mode must be raw or safe");
        }
        publish("orders-crash-" + mode, event.customerId(), event, false);
    }

    private void publish(String topic, String key, OrderEvent event, boolean useRoundRobin) {
        try {
            String json = mapper.writeValueAsString(event);
            var result = (useRoundRobin ? roundRobin : keyed)
                    .send(topic, key, json).get(15, TimeUnit.SECONDS);
            log.info("ORDER_PUBLISHED mode={} runId={} customer={} seq={} key={} partition={} offset={} eventId={}",
                    event.mode(), event.runId(), event.customerId(), event.seq(), key,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), event.eventId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during Kafka publish: " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka publish failed: " + topic, e);
        }
    }
}
