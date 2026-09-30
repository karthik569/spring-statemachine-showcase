# Spring State Machine Showcase 🔄

A production-grade finite state machine application managing lifecycle transitions, event triggers, guards, and multi-instance orchestration using **Spring State Machine 4.0**.

---

## 🌟 Comprehensive Method-by-Method Breakdown

### 1. [`OrderStateMachineConfig`](file:///sdcard/Download/termux/spring-statemachine-showcase/src/main/java/com/example/statemachine/config/OrderStateMachineConfig.java)
- **`void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states)`**: Defines the finite state hierarchy, including payment review and return states, with `RETURNED` and `CANCELLED` as terminal states.
- **`void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions)`**: Configures lifecycle pathways, a choice pseudostate for risk-based payment routing, guarded payment and dispatch retries, and the 30-day return-window guard.

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
- `GET /api/workflow/orders/summary` returns total, active, delivered, returned, and cancelled order counts, including counts for each state.
- `GET /api/workflow/orders/stale?hours=24` finds non-terminal orders with no recorded activity for the requested number of hours. The threshold accepts 1–720 hours; results are ordered from longest inactive to shortest.
- `GET /api/workflow/orders/{orderId}/state` returns the current state, payment and dispatch retry counts, return deadline (when delivered), and the events currently allowed from it.
- `GET /api/workflow/orders/{orderId}/available-events` returns events allowed by the current state and guards.
- `GET /api/workflow/orders/{orderId}/history` returns its event history.
- `GET /api/workflow/orders/{orderId}/history/search` filters history by event and/or acceptance and returns the newest matching entries, with a limit from 1 to 500 and a `hasMore` flag.
- `GET /api/workflow/orders/details` lists orders with customer details and creation time.
- `GET /api/workflow/orders/details?page=0&size=20` returns a paginated, newest-first order list. `size` accepts values from 1 to 100.
- `GET /api/workflow/orders/details?state=PREPARING` filters detailed results by workflow state.
- `GET /api/workflow/orders/details?q=asha` searches order IDs, customer names, and customer email addresses case-insensitively.
- The detailed listing response includes `totalOrders`, `totalPages`, `hasNext`, and `hasPrevious` pagination metadata.
- `GET /api/workflow/orders/{orderId}/details` returns one order's details.
- `POST /api/workflow/orders/{orderId}/event?event=...` returns HTTP 409 with the current state and allowed events when the requested transition is not valid.
- `POST /api/workflow/orders/events/bulk` applies one event to up to 100 orders and returns a result for each order, including missing orders and invalid transitions.

Create an order with optional customer data by posting JSON to `/api/workflow/orders/create`:

```bash
curl -i -X POST http://localhost:8087/api/workflow/orders/create \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Asha Rao","customerEmail":"asha@example.com"}'
```

The existing empty-body create request remains supported.

When supplied, `customerName` must contain 1–120 characters and `customerEmail` must be a valid email address of at most 254 characters. Invalid fields return HTTP 400 with an `errors` object keyed by field name.

Orders can be cancelled while `SUBMITTED`, `PAYMENT_PENDING`, `PAYMENT_FAILED`, `PAID`, `PREPARING`, or `DISPATCH_FAILED`. Once dispatched, an order can no longer be cancelled through this workflow.

### Guarded payment retries

When `PAYMENT_FAILED` is received in `PAYMENT_PENDING`, the machine moves to the non-terminal `PAYMENT_FAILED` state. From there, `RETRY_PAYMENT` returns the order to `PAYMENT_PENDING` while fewer than three retries have been used. The retry count is stored in the state machine's extended state per order. After the third retry, the guard rejects further retries, and `RETRY_PAYMENT` no longer appears in the available events. Failed-payment orders can still be cancelled with `CANCEL`.

```bash
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=PAYMENT_FAILED"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=RETRY_PAYMENT"
curl -s "http://localhost:8087/api/workflow/orders/<ORDER_ID>/state"
```

The order state response includes `paymentRetriesUsed`; rejected retry requests return HTTP 409 with the reason `Maximum payment retries reached.`

### Choice pseudostate for payment review

`PAYMENT_SUCCESS` enters a choice pseudostate. A `paymentRiskScore` below 70 routes directly to `PAID`; a score of 70 or higher routes to `PAYMENT_REVIEW`. If no score is supplied, the machine treats the payment as low risk. A review can be resolved with `APPROVE_PAYMENT_REVIEW` or `REJECT_PAYMENT_REVIEW`, and can be cancelled while it is pending.

```bash
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=PAYMENT_SUCCESS&paymentRiskScore=82"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=APPROVE_PAYMENT_REVIEW"
```

Scores must be between 0 and 100. The score is stored in that order's extended state and evaluated by a guarded choice branch.

### Guarded dispatch retries

If carrier handoff fails in `PREPARING`, `DISPATCH_FAILED` moves the order into `DISPATCH_FAILED`. `RETRY_DISPATCH` returns it to `PREPARING`, where dispatch can be attempted again. Each order gets at most three retries. When the limit is reached, the guard rejects further retries, the retry event disappears from available events, and the order can still be cancelled. The state response includes `dispatchRetriesUsed`.

```bash
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=DISPATCH_FAILED"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=RETRY_DISPATCH"
curl -s "http://localhost:8087/api/workflow/orders/<ORDER_ID>/state"
```

### Guarded returns workflow

When `DELIVER` moves a shipment into `DELIVERED`, the state machine records the delivery time and opens a 30-day return window. `REQUEST_RETURN` is available only before the deadline. A request can be approved with `APPROVE_RETURN`, then completed with `RECEIVE_RETURN`, which moves the order to terminal state `RETURNED`. A request can instead be rejected with `REJECT_RETURN`, returning the order to `DELIVERED`. The order state endpoint reports `returnWindowEndsAt`, and the available-events endpoint omits `REQUEST_RETURN` after expiry.

```bash
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=REQUEST_RETURN"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=APPROVE_RETURN"
curl -i -X POST "http://localhost:8087/api/workflow/orders/<ORDER_ID>/event?event=RECEIVE_RETURN"
```

Find active orders that have been inactive for at least 48 hours:

```bash
curl -s "http://localhost:8087/api/workflow/orders/stale?hours=48"
```

Each result includes the current state, customer details, creation time, last recorded event time, and inactive duration in seconds. Orders in `DELIVERED` or `CANCELLED` are excluded.

Search the latest accepted `PAY` history entries for an order:

```bash
curl -s "http://localhost:8087/api/workflow/orders/ORD-12345678/history/search?event=PAY&accepted=true&limit=25"
```

The response includes the filter values, total number of matching entries, the selected entries in chronological order, and `hasMore` when older matching history exists.

Send a lifecycle event to multiple orders in one request:

```bash
curl -i -X POST http://localhost:8087/api/workflow/orders/events/bulk \
  -H "Content-Type: application/json" \
  -d '{"orderIds":["ORD-12345678","ORD-87654321"],"event":"CANCEL"}'
```

The request accepts 1–100 unique, non-blank order IDs and one event. Each result reports `ACCEPTED`, `REJECTED` (invalid from the current state), or `NOT_FOUND`; one failure does not prevent the other orders from being processed. The response includes accepted and rejected counts.

## Logging

Application logs are written to `logs/application.log` and the terminal when the app is started with `mvn spring-boot:run`. The active file rotates at 10 MB; compressed archives are kept for up to 14 days, with a 100 MB archive size cap, under `logs/archive/`.

Test logs are written to `logs/tests/test.log` and the test process console when running `mvn test`. Test log archives use the same rotation limits and are stored under `logs/tests/archive/`.

## Detailed listing example

```bash
curl -s "http://localhost:8087/api/workflow/orders/details?q=asha&state=PAID&page=0&size=10"
```

The response has this shape:

```json
{
  "orders": [],
  "page": 0,
  "size": 10,
  "query": "asha",
  "totalOrders": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```
