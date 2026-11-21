package com.example.order.kafka;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CrashEventStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public CrashEventStore(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    // Database transaction commits BEFORE the caller can halt the JVM; Kafka ack happens afterwards.
    public SaveResult save(OrderEvent event, int partition, long offset, boolean crashArmed) {
        return transactions.execute(status -> {
            boolean duplicate = false;
            if ("safe".equals(event.mode())) {
                int claimed = jdbc.update("INSERT INTO processed_event(event_id) VALUES (?) ON CONFLICT DO NOTHING",
                        event.eventId());
                duplicate = claimed == 0;
            }
            if (!duplicate) {
                jdbc.update("INSERT INTO lab_event_log(event_id,mode,customer_id,seq,kafka_partition,kafka_offset) "
                                + "VALUES (?,?,?,?,?,?)",
                        event.eventId(), event.mode(), event.customerId(), event.seq(), partition, offset);
            }
            boolean crash = false;
            if (crashArmed) {
                // Durable marker prevents an infinite crash loop on the next process start.
                crash = jdbc.update("INSERT INTO crash_marker(marker) VALUES (?) ON CONFLICT DO NOTHING",
                        event.mode() + ":" + event.eventId()) == 1;
            }
            return new SaveResult(duplicate, crash);
        });
    }

    public Map<String, Object> results(String eventId) {
        Integer rawRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_event_log WHERE event_id=? AND mode='raw'", Integer.class, eventId);
        Integer safeRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lab_event_log WHERE event_id=? AND mode='safe'", Integer.class, eventId);
        Integer dedupRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id=?", Integer.class, eventId);
        return Map.of("eventId", eventId, "rawRows", rawRows,
                "safeRows", safeRows, "processedEventRows", dedupRows);
    }

    public record SaveResult(boolean duplicate, boolean crash) { }
}
