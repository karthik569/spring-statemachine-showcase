package com.example.statemachine;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import com.example.statemachine.service.OrderWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrderPaymentRetryStateMachineTest {

    @Autowired
    private OrderWorkflowService workflowService;

    @Test
    void retriesPaymentUpToThreeTimesThenGuardRejectsAnotherRetry() {
        String orderId = "ORD-RETRY-" + UUID.randomUUID();
        workflowService.createOrder(orderId);
        assertEquals(OrderStates.SUBMITTED, workflowService.getOrderState(orderId));

        assertTrue(workflowService.sendEvent(orderId, OrderEvents.PAY).accepted());
        assertEquals(OrderStates.PAYMENT_PENDING, workflowService.getOrderState(orderId));

        for (int retry = 1; retry <= 3; retry++) {
            assertTrue(workflowService.sendEvent(orderId, OrderEvents.PAYMENT_FAILED).accepted());
            assertEquals(OrderStates.PAYMENT_FAILED, workflowService.getOrderState(orderId));
            assertTrue(workflowService.getAvailableEvents(orderId).contains(OrderEvents.RETRY_PAYMENT));

            assertTrue(workflowService.sendEvent(orderId, OrderEvents.RETRY_PAYMENT).accepted());
            assertEquals(OrderStates.PAYMENT_PENDING, workflowService.getOrderState(orderId));
            assertEquals(retry, workflowService.getPaymentRetryCount(orderId));
        }

        assertTrue(workflowService.sendEvent(orderId, OrderEvents.PAYMENT_FAILED).accepted());
        assertFalse(workflowService.getAvailableEvents(orderId).contains(OrderEvents.RETRY_PAYMENT));

        var blockedRetry = workflowService.sendEvent(orderId, OrderEvents.RETRY_PAYMENT);
        assertFalse(blockedRetry.accepted());
        assertEquals("Maximum payment retries reached.", blockedRetry.reason());
        assertEquals(OrderStates.PAYMENT_FAILED, workflowService.getOrderState(orderId));

        assertTrue(workflowService.sendEvent(orderId, OrderEvents.CANCEL).accepted());
        assertEquals(OrderStates.CANCELLED, workflowService.getOrderState(orderId));
    }

    @Test
    void deliveredOrderCanCompleteAReturnWorkflow() {
        String orderId = "ORD-RETURN-" + UUID.randomUUID();
        workflowService.createOrder(orderId);
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.PAY).accepted());
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.PAYMENT_SUCCESS).accepted());
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.START_PREPARING).accepted());
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.DISPATCH).accepted());
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.DELIVER).accepted());

        assertEquals(OrderStates.DELIVERED, workflowService.getOrderState(orderId));
        assertTrue(workflowService.isReturnWindowOpen(orderId));
        assertTrue(workflowService.getReturnWindowEndsAt(orderId) != null);
        assertTrue(workflowService.getAvailableEvents(orderId).contains(OrderEvents.REQUEST_RETURN));

        assertTrue(workflowService.sendEvent(orderId, OrderEvents.REQUEST_RETURN).accepted());
        assertEquals(OrderStates.RETURN_REQUESTED, workflowService.getOrderState(orderId));
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.APPROVE_RETURN).accepted());
        assertEquals(OrderStates.RETURN_APPROVED, workflowService.getOrderState(orderId));
        assertTrue(workflowService.sendEvent(orderId, OrderEvents.RECEIVE_RETURN).accepted());
        assertEquals(OrderStates.RETURNED, workflowService.getOrderState(orderId));
        assertTrue(workflowService.getAvailableEvents(orderId).isEmpty());
    }
}
