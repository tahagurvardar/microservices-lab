package com.example.order.kafka;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class CrashArm {
    private final AtomicReference<String> mode = new AtomicReference<>();

    public void arm(String selectedMode) {
        if (!selectedMode.equals("raw") && !selectedMode.equals("safe")) {
            throw new IllegalArgumentException("mode must be raw or safe");
        }
        mode.set(selectedMode);
    }

    public boolean isArmed(String selectedMode) {
        return selectedMode.equals(mode.get());
    }

    public boolean consume(String selectedMode) {
        return mode.compareAndSet(selectedMode, null);
    }
}
