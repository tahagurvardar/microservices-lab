package com.example.order.kafka;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CrashArmTest {
    @Test
    void crashMustBeExplicitlyArmedAndIsOneShot() {
        CrashArm arm = new CrashArm();
        assertFalse(arm.consume("raw"));
        arm.arm("raw");
        assertFalse(arm.consume("safe"));
        assertTrue(arm.consume("raw"));
        assertFalse(arm.consume("raw"));
    }

    @Test
    void invalidModeIsRejected() {
        CrashArm arm = new CrashArm();
        assertThrows(IllegalArgumentException.class, () -> arm.arm("other"));
    }
}
