package com.example.statemachine;

import com.example.statemachine.model.OrderMachineVariables;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMachineVariablesTest {

    @Test
    void returnWindowIsOpenBeforeThirtyDayDeadline() {
        Instant deliveredAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant beforeDeadline = Instant.parse("2026-01-30T23:59:59Z");

        assertTrue(OrderMachineVariables.isReturnWindowOpen(deliveredAt, beforeDeadline));
    }

    @Test
    void returnWindowClosesAtItsThirtyDayDeadline() {
        Instant deliveredAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant deadline = Instant.parse("2026-01-31T00:00:00Z");

        assertFalse(OrderMachineVariables.isReturnWindowOpen(deliveredAt, deadline));
        assertFalse(OrderMachineVariables.isReturnWindowOpen(deliveredAt, deadline.plusSeconds(1)));
    }

    @Test
    void returnWindowIsClosedWithoutDeliveryTimestamp() {
        assertFalse(OrderMachineVariables.isReturnWindowOpen(null, Instant.now()));
    }
}
