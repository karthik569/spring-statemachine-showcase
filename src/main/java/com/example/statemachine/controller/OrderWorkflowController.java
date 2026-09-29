package com.example.statemachine.controller;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import com.example.statemachine.service.OrderWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * REST controller orchestrating finite state machine transitions for customer order lifecycles.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>Dynamically spawning isolated state machine instances per order ID</li>
 *   <li>Dispatching deterministic event signals (e.g. {@code PAY}, {@code PAYMENT_SUCCESS}, {@code DISPATCH})</li>
 *   <li>State inspection and transition validation</li>
 * </ul>
 *
 * @author Spring Showcase Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/workflow/orders")
public class OrderWorkflowController {

    private final OrderWorkflowService workflowService;

    /**
     * Constructs the workflow controller with the state machine service.
     *
     * @param workflowService service managing state machine instances and transition routing
     */
    public OrderWorkflowController(OrderWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Initializes a brand new order workflow instance in the initial {@link OrderStates#SUBMITTED} state.
     *
     * @return response entity containing the generated order ID and initial state
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createOrder() {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var sm = workflowService.createOrder(orderId);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "currentState", sm.getState().getId(),
                "message", "Order workflow initialized in SUBMITTED state."
        ));
    }

    /**
     * Dispatches an event signal to the specified order's state machine to trigger a state transition.
     *
     * @param orderId the order instance ID
     * @param event   the event to send (e.g. {@link OrderEvents#PAY}, {@link OrderEvents#DELIVER})
     * @return response mapping detailing state before, state after, and whether the transition guard accepted the event
     */
    @PostMapping("/{orderId}/event")
    public ResponseEntity<Map<String, Object>> triggerEvent(
            @PathVariable String orderId,
            @RequestParam OrderEvents event) {

        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }

        OrderStates stateBefore = workflowService.getOrderState(orderId);
        boolean accepted = workflowService.sendEvent(orderId, event);
        OrderStates stateAfter = workflowService.getOrderState(orderId);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "eventSent", event,
                "previousState", stateBefore,
                "currentState", stateAfter,
                "transitionAccepted", accepted
        ));
    }

    /**
     * Queries the current finite state of an order workflow.
     *
     * @param orderId the order instance identifier
     * @return response containing current {@link OrderStates}
     */
    @GetMapping("/{orderId}/state")
    public ResponseEntity<Map<String, Object>> getOrderState(@PathVariable String orderId) {
        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }
        OrderStates state = workflowService.getOrderState(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "currentState", state,
                "availableEvents", workflowService.getAvailableEvents(orderId)
        ));
    }

    @GetMapping("/{orderId}/available-events")
    public ResponseEntity<?> getAvailableEvents(@PathVariable String orderId) {
        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "currentState", workflowService.getOrderState(orderId),
                "availableEvents", workflowService.getAvailableEvents(orderId)
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, OrderStates>> getOrders(
            @RequestParam(required = false) OrderStates state) {
        Map<String, OrderStates> orders = workflowService.getOrders();
        if (state != null) {
            orders = orders.entrySet().stream()
                    .filter(entry -> entry.getValue() == state)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getWorkflowSummary() {
        Map<OrderStates, Long> counts = workflowService.getOrderCountsByState();
        long delivered = counts.get(OrderStates.DELIVERED);
        long cancelled = counts.get(OrderStates.CANCELLED);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return ResponseEntity.ok(Map.of(
                "totalOrders", total,
                "activeOrders", total - delivered - cancelled,
                "deliveredOrders", delivered,
                "cancelledOrders", cancelled,
                "ordersByState", counts
        ));
    }

    @GetMapping("/{orderId}/history")
    public ResponseEntity<?> getOrderHistory(@PathVariable String orderId) {
        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(workflowService.getHistory(orderId));
    }
}
