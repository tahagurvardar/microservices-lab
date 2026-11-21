package com.example.order.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class OrderSequenceTracker {
    private final Map<String, Map<String, List<Integer>>> runs = new ConcurrentHashMap<>();

    public boolean record(OrderEvent event) {
        Map<String, List<Integer>> customers = runs.computeIfAbsent(
                event.runId(), ignored -> new ConcurrentHashMap<>());
        List<Integer> seqs = customers.computeIfAbsent(
                event.customerId(), ignored -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (seqs) {
            seqs.add(event.seq());
            // Preserve an out-of-order finding even when a later event has the expected sequence number.
            return !isOrdered(seqs);
        }
    }

    public Map<String, Object> observations(String runId) {
        Map<String, List<Integer>> customers = runs.getOrDefault(runId, Map.of());
        Map<String, List<Integer>> snapshot = new LinkedHashMap<>();
        customers.keySet().stream().sorted().forEach(customer -> {
            List<Integer> seqs = customers.get(customer);
            synchronized (seqs) {
                snapshot.put(customer, List.copyOf(seqs));
            }
        });
        long outOfOrder = snapshot.values().stream().filter(s -> !isOrdered(s)).count();
        return Map.of("runId", runId, "customerSequences", snapshot,
                "outOfOrderCustomers", outOfOrder, "observedCustomers", snapshot.size());
    }

    public static boolean isOrdered(List<Integer> seqs) {
        for (int i = 0; i < seqs.size(); i++) {
            if (seqs.get(i) != i + 1) {
                return false;
            }
        }
        return true;
    }
}
