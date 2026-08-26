# Spring State Machine - In-Depth Architecture & Method Guide 🔄

This document provides a comprehensive, method-by-method technical deep dive into `spring-statemachine-showcase`. It details finite state definitions, transition events, transition guards, and multi-instance orchestration using `StateMachineFactory`.

---

## 1. Project Overview & Workflow Architecture

The application runs on port `8087` using **Spring State Machine 4.0**. It demonstrates:
1. **Finite State Workflows**: Typed states (`SUBMITTED`, `PAYMENT_PENDING`, `PAID`, `PREPARING`, `SHIPPED`, `DELIVERED`, `CANCELLED`).
2. **Transition Events**: Typed events (`PAY`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `START_PREPARING`, `DISPATCH`, `DELIVER`, `CANCEL`).
3. **Multi-Instance State Management**: Using `StateMachineFactory<OrderStates, OrderEvents>` to spawn and maintain isolated, independent workflow instances for concurrent orders.

```
 [SUBMITTED] ──(PAY)──► [PAYMENT_PENDING] ──(PAYMENT_SUCCESS)──► [PAID]
      │                         │                                  │
  (CANCEL)                  (PAYMENT_FAILED)                 (START_PREPARING)
      │                         │                                  │
      ▼                         ▼                                  ▼
 [CANCELLED]               [CANCELLED]                        [PREPARING]
                                                                   │
                                                               (DISPATCH)
                                                                   │
                                                                   ▼
                                                               [SHIPPED]
                                                                   │
                                                                (DELIVER)
                                                                   │
                                                                   ▼
                                                              [DELIVERED]
```

---

## 2. In-Depth Class & Method Breakdown

### A. State Machine Configuration Layer

#### [`OrderStateMachineConfig.java`](file:///sdcard/Download/termux/spring-statemachine-showcase/src/main/java/com/example/statemachine/config/OrderStateMachineConfig.java)
- **`void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states)`**:
  - *Purpose*: Defines the state hierarchy and lifecycle boundaries.
  - *Initial State*: Sets `OrderStates.SUBMITTED` as the entry state.
  - *End States*: Defines `OrderStates.DELIVERED` and `OrderStates.CANCELLED` as terminal end states.
- **`void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions)`**:
  - *Purpose*: Defines valid event-triggered transition pathways:
    - `SUBMITTED` + `PAY` &rarr; `PAYMENT_PENDING`
    - `PAYMENT_PENDING` + `PAYMENT_SUCCESS` &rarr; `PAID`
    - `PAYMENT_PENDING` + `PAYMENT_FAILED` &rarr; `CANCELLED`
    - `PAID` + `START_PREPARING` &rarr; `PREPARING`
    - `PREPARING` + `DISPATCH` &rarr; `SHIPPED`
    - `SHIPPED` + `DELIVER` &rarr; `DELIVERED`
    - `SUBMITTED` + `CANCEL` &rarr; `CANCELLED`

---

### B. Workflow Service Layer

#### [`OrderWorkflowService.java`](file:///sdcard/Download/termux/spring-statemachine-showcase/src/main/java/com/example/statemachine/service/OrderWorkflowService.java)
- **`StateMachine<OrderStates, OrderEvents> createOrder(String orderId)`**:
  - *Operation*: Calls `stateMachineFactory.getStateMachine(orderId)`, starts the state machine, and stores it in the in-memory `orderStateMachines` map under the unique order ID.
- **`boolean sendEvent(String orderId, OrderEvents event)`**:
  - *Inputs*: `orderId`, `event` (e.g. `PAY`, `DISPATCH`).
  - *Operation*: Fetches the order's active state machine and sends the event via `sm.sendEvent(MessageBuilder.withPayload(event).build())`.
  - *Return*: Returns `true` if the state transition was valid and accepted; returns `false` if the event is illegal for the current state (e.g. sending `DELIVER` while in `SUBMITTED`).
- **`OrderStates getOrderState(String orderId)`**: Returns the current state enum value.

---

### C. REST Controller Layer

#### [`OrderWorkflowController.java`](file:///sdcard/Download/termux/spring-statemachine-showcase/src/main/java/com/example/statemachine/controller/OrderWorkflowController.java)
- **`POST /api/workflow/orders/create`**: Calls `workflowService.createOrder(orderId)` and returns the initialized `SUBMITTED` state.
- **`POST /api/workflow/orders/{id}/event?event=PAY`**: Dispatches transition events and returns previous state, new state, and transition acceptance status.
- **`GET /api/workflow/orders/{id}/state`**: Queries the current finite state of the order.
