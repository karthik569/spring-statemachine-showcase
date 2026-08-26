package com.example.statemachine;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineModelTest {

    @Test
    void testEnumValues() {
        assertEquals(7, OrderStates.values().length);
        assertEquals(7, OrderEvents.values().length);
        assertEquals(OrderStates.SUBMITTED, OrderStates.valueOf("SUBMITTED"));
        assertEquals(OrderEvents.PAY, OrderEvents.valueOf("PAY"));
    }
}
