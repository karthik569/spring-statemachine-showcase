package com.example.statemachine.controller;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import com.example.statemachine.service.OrderWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    public ResponseEntity<Map<String, Object>> createOrder(
            @Valid @RequestBody(required = false) CreateOrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String customerName = request == null ? null : request.customerName();
        String customerEmail = request == null ? null : request.customerEmail();
        var sm = workflowService.createOrder(orderId, customerName, customerEmail);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", orderId);
        response.put("currentState", sm.getState().getId());
        response.put("customerName", customerName);
        response.put("customerEmail", customerEmail);
        response.put("message", "Order workflow initialized in SUBMITTED state.");
        return ResponseEntity.status(201).body(response);
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
        var outcome = workflowService.sendEvent(orderId, event);
        OrderStates stateAfter = workflowService.getOrderState(orderId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", orderId);
        response.put("eventSent", event);
        response.put("previousState", stateBefore);
        response.put("currentState", stateAfter);
        response.put("transitionAccepted", outcome.accepted());
        if (!outcome.accepted()) {
            response.put("reason", outcome.reason());
            response.put("availableEvents", workflowService.getAvailableEvents(orderId));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/events/bulk")
    public ResponseEntity<?> triggerBulkEvent(@Valid @RequestBody BulkEventRequest request) {
        if (new HashSet<>(request.orderIds()).size() != request.orderIds().size()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "orderIds must not contain duplicates"));
        }

        List<BulkEventResult> results = request.orderIds().stream().map(orderId -> {
            if (!workflowService.orderExists(orderId)) {
                return new BulkEventResult(orderId, "NOT_FOUND", null, null, false,
                        "Order was not found.");
            }

            OrderStates previousState = workflowService.getOrderState(orderId);
            var outcome = workflowService.sendEvent(orderId, request.event());
            OrderStates currentState = workflowService.getOrderState(orderId);
            return new BulkEventResult(orderId, outcome.accepted() ? "ACCEPTED" : "REJECTED",
                    previousState, currentState, outcome.accepted(), outcome.reason());
        }).toList();

        long acceptedCount = results.stream().filter(BulkEventResult::transitionAccepted).count();
        return ResponseEntity.ok(new BulkEventResponse(request.event(), results, results.size(),
                acceptedCount, results.size() - acceptedCount));
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
        var details = workflowService.getOrderDetails(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "currentState", state,
                "availableEvents", workflowService.getAvailableEvents(orderId),
                "customer", details
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

    @GetMapping("/details")
    public ResponseEntity<Map<String, Object>> getOrdersWithDetails(
            @RequestParam(required = false) OrderStates state,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "page must be non-negative and size must be between 1 and 100"));
        }
        List<OrderView> orders = workflowService.getOrdersWithDetails(state, q, page, size).stream()
                .map(details -> new OrderView(details.orderId(), workflowService.getOrderState(details.orderId()),
                        details.customerName(), details.customerEmail(), details.createdAt()))
                .toList();
        long total = workflowService.countOrders(state, q);
        return ResponseEntity.ok(Map.of(
                "orders", orders,
                "page", page,
                "size", size,
                "query", q == null ? "" : q,
                "totalOrders", total,
                "totalPages", total == 0 ? 0 : (total + size - 1) / size,
                "hasNext", (long) (page + 1) * size < total,
                "hasPrevious", page > 0
        ));
    }

    @GetMapping("/{orderId}/details")
    public ResponseEntity<?> getOrderDetails(@PathVariable String orderId) {
        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }
        var details = workflowService.getOrderDetails(orderId);
        return ResponseEntity.ok(new OrderView(details.orderId(), workflowService.getOrderState(orderId),
                details.customerName(), details.customerEmail(), details.createdAt()));
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

    @GetMapping("/stale")
    public ResponseEntity<?> getStaleOrders(@RequestParam(defaultValue = "24") int hours) {
        if (hours < 1 || hours > 720) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "hours must be between 1 and 720"));
        }
        List<StaleOrderView> orders = workflowService.getStaleOrders(Duration.ofHours(hours)).stream()
                .map(stale -> new StaleOrderView(stale.details().orderId(), stale.currentState(),
                        stale.details().customerName(), stale.details().customerEmail(),
                        stale.details().createdAt(), stale.lastActivityAt(), stale.inactiveForSeconds()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "thresholdHours", hours,
                "totalOrders", orders.size(),
                "orders", orders
        ));
    }

    @GetMapping("/{orderId}/history")
    public ResponseEntity<?> getOrderHistory(@PathVariable String orderId) {
        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(workflowService.getHistory(orderId));
    }

    @GetMapping("/{orderId}/history/search")
    public ResponseEntity<?> searchOrderHistory(
            @PathVariable String orderId,
            @RequestParam(required = false) OrderEvents event,
            @RequestParam(required = false) Boolean accepted,
            @RequestParam(defaultValue = "100") int limit) {
        if (!workflowService.orderExists(orderId)) {
            return ResponseEntity.notFound().build();
        }
        if (limit < 1 || limit > 500) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "limit must be between 1 and 500"));
        }

        var page = workflowService.getHistory(orderId, event, accepted, limit);
        return ResponseEntity.ok(new HistorySearchResponse(orderId, event, accepted, limit,
                page.totalEntries(), page.totalEntries() > page.entries().size(), page.entries()));
    }

    public record CreateOrderRequest(
            @NotBlank(message = "must not be blank")
            @Size(max = 120, message = "must be at most 120 characters")
            String customerName,
            @NotBlank(message = "must not be blank")
            @Email(message = "must be a valid email address")
            @Size(max = 254, message = "must be at most 254 characters")
            String customerEmail) {
    }

    public record BulkEventRequest(
            @NotEmpty(message = "must contain at least one order ID")
            @Size(max = 100, message = "must contain at most 100 order IDs")
            List<@NotBlank(message = "must not be blank") String> orderIds,
            @NotNull(message = "must not be null") OrderEvents event) {
    }

    public record BulkEventResponse(OrderEvents event, List<BulkEventResult> results,
                                    int totalOrders, long acceptedCount, long rejectedCount) {
    }

    public record BulkEventResult(String orderId, String status, OrderStates previousState,
                                  OrderStates currentState, boolean transitionAccepted, String reason) {
    }

    public record StaleOrderView(String orderId, OrderStates currentState, String customerName,
                                 String customerEmail, java.time.Instant createdAt,
                                 java.time.Instant lastActivityAt, long inactiveForSeconds) {
    }

    public record HistorySearchResponse(String orderId, OrderEvents event, Boolean accepted, int limit,
                                        int totalEntries, boolean hasMore,
                                        List<OrderWorkflowService.TransitionRecord> entries) {
    }

    public record OrderView(String orderId, OrderStates currentState, String customerName,
                            String customerEmail, java.time.Instant createdAt) {
    }
}
