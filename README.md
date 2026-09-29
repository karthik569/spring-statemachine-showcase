# Spring State Machine Showcase 🔄

A production-grade finite state machine application managing lifecycle transitions, event triggers, guards, and multi-instance orchestration using **Spring State Machine 4.0**.

---

## 🌟 Comprehensive Method-by-Method Breakdown

### 1. [`OrderStateMachineConfig`](file:///sdcard/Download/termux/spring-statemachine-showcase/src/main/java/com/example/statemachine/config/OrderStateMachineConfig.java)
- **`void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states)`**: Defines the finite state hierarchy (`SUBMITTED` initial, `PAYMENT_PENDING`, `PAID`, `PREPARING`, `SHIPPED`, `DELIVERED` terminal, `CANCELLED` terminal).
- **`void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions)`**: Configures deterministic transition pathways (e.g. `SUBMITTED -> PAY -> PAYMENT_PENDING`, `PAYMENT_PENDING -> PAYMENT_SUCCESS -> PAID`, etc.) and attaches condition guards.

### 2. [`OrderWorkflowController`](file:///sdcard/Download/termux/spring-statemachine-showcase/src/main/java/com/example/statemachine/controller/OrderWorkflowController.java)
- **`ResponseEntity<Map<String, Object>> createOrder()`**: `POST /api/workflow/orders/create`; instantiates a new isolated state machine instance from `StateMachineFactory<OrderState, OrderEvent>` and sets initial state `SUBMITTED`.
- **`ResponseEntity<Map<String, Object>> sendEvent(orderId, event)`**: `POST /api/workflow/orders/{id}/event`; dispatches lifecycle event signals to the order's state machine to transition state.
- **`ResponseEntity<Map<String, Object>> getOrderState(orderId)`**: `GET /api/workflow/orders/{id}/state`; queries the current finite state of the order instance.

---

## 🚀 How to Run in Termux

### 1. Build and Run Tests
```bash
cd /sdcard/Download/termux/spring-statemachine-showcase
mvn clean test
```

### 2. Start Application on Port 8087
```bash
mvn spring-boot:run
```

---

## 🧪 Interactive API Testing & cURL Commands

### 1. Initialize Order Workflow
```bash
curl -i -X POST http://localhost:8087/api/workflow/orders/create
```

---

### 2. Dispatch Lifecycle Events
```bash
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=PAY"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=PAYMENT_SUCCESS"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=START_PREPARING"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=DISPATCH"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=DELIVER"
```

## Workflow discovery

- `GET /api/workflow/orders` lists in-memory orders and their current states.
- `GET /api/workflow/orders?state=PREPARING` filters the order list by state.
- `GET /api/workflow/orders/summary` returns total, active, delivered, and cancelled order counts, including counts for each state.
- `GET /api/workflow/orders/{orderId}/state` returns the current state and the events currently allowed from it.
- `GET /api/workflow/orders/{orderId}/available-events` returns the events currently allowed for an order.
- `GET /api/workflow/orders/{orderId}/history` returns its event history.

Orders can be cancelled while `SUBMITTED`, `PAYMENT_PENDING`, `PAID`, or `PREPARING`. Once dispatched, an order can no longer be cancelled through this workflow.
