package com.example.statemachine.controller;

import com.example.statemachine.model.OrderStates;
import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.service.OrderWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderWorkflowControllerTest {

    private final OrderWorkflowService workflowService = mock(OrderWorkflowService.class);
    private final OrderWorkflowController controller = new OrderWorkflowController(workflowService);

    @Test
    void returnsStaleOrdersForRequestedThreshold() {
        var details = new OrderWorkflowService.OrderDetails("ORD-STALE", "Asha Rao",
                "asha@example.com", Instant.parse("2026-09-01T10:00:00Z"));
        var staleOrder = new OrderWorkflowService.StaleOrder(details, OrderStates.PREPARING,
                Instant.parse("2026-09-02T10:00:00Z"), 172800);
        when(workflowService.getStaleOrders(Duration.ofHours(48))).thenReturn(List.of(staleOrder));

        var response = controller.getStaleOrders(48);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(48, body.get("thresholdHours"));
        assertEquals(1, body.get("totalOrders"));
        assertEquals(1, ((List<?>) body.get("orders")).size());
        verify(workflowService).getStaleOrders(Duration.ofHours(48));
    }

    @Test
    void rejectsThresholdOutsideSupportedRange() {
        var response = controller.getStaleOrders(721);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        verifyNoInteractions(workflowService);
    }

    @Test
    void searchesRecentOrderHistoryWithFilters() {
        when(workflowService.orderExists("ORD-STALE")).thenReturn(true);
        var entry = new OrderWorkflowService.TransitionRecord(Instant.parse("2026-09-02T10:00:00Z"),
                OrderEvents.PAY, OrderStates.SUBMITTED, OrderStates.PAYMENT_PENDING, true);
        when(workflowService.getHistory("ORD-STALE", OrderEvents.PAY, true, 25))
                .thenReturn(new OrderWorkflowService.HistoryPage(List.of(entry), 3));

        var response = controller.searchOrderHistory("ORD-STALE", OrderEvents.PAY, true, 25);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(OrderWorkflowController.HistorySearchResponse.class, response.getBody());
        assertEquals(3, body.totalEntries());
        assertTrue(body.hasMore());
        assertEquals(List.of(entry), body.entries());
        verify(workflowService).getHistory("ORD-STALE", OrderEvents.PAY, true, 25);
    }

    @Test
    void rejectsHistoryLimitOutsideSupportedRange() {
        when(workflowService.orderExists("ORD-STALE")).thenReturn(true);

        var response = controller.searchOrderHistory("ORD-STALE", null, null, 501);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
