# Phase 5: Order Tracking & Delivery Consumption — Implementation Plan

## 1. Module Map
- **Modules**: `delivery-tracking` (new), `order` (existing).
- **Dependencies**: `delivery-tracking` consumes `order` state. `order` MUST NOT depend on `delivery-tracking` (acyclic). The core state machine (`pack()`, `dispatch()`, `deliver()`) lives in `order`.

## 2. Domain Model
- **New Entities**:
  - `DeliveryTrackingEvent`: Append-only audit log of tracking updates. Fields: `id`, `orderId`, `status`, `latitude` (nullable), `longitude` (nullable), `recordedAt`. Same shape as `LoginEvent`.
- **Order Aggregate Extensions**:
  - `placedAt`: Timestamp of creation/payment (Missing from Phase 4; to be mapped from DB `created_at` or added explicitly).
  - `driverId` / `driverPhone`: Nullable, set upon transition to `DISPATCHED`.
- **Ports**:
  - `CallProxyGateway`: Adapter port for driver-customer communication masking (Exotel/Knowlarity). Same shape as `PaymentGateway`. Stub implementation (`DummyCallProxyGateway`) initially.

## 3. Key Architecture Decisions
- **(a) WebSocket Auth/Scoping**: Per-order topic scoped by JWT claim at subscribe time. Spring WebSocket with STOMP will use a `ChannelInterceptor`. The initial WebSocket connection/handshake will pass the JWT (via query param or connect frame header), verified by the existing `TokenValidator`. Upon `SUBSCRIBE /topic/orders/{id}`, the interceptor will verify the `userId` in the token matches the `Order.userId`.
- **(b) Polling Fallback (`GET /orders/{id}/tracking`)**: Simple direct DB read. Fetches the `Order` status and the latest `DeliveryTrackingEvent` row via a fast descending-sort query. No CQRS or separate read model is needed.
- **(c) Webhook Security (`PUT /orders/{id}/status`)**: Internal-only endpoint for delivery partners. Will be secured via a shared static API Key (e.g., `X-API-Key` header) validated via interceptor or security config. **Flag:** This is a real security boundary. We will use a dummy/open configuration for local `dev`, but the header enforcement must be structurally present.
- **(d) Reschedule Logic (Modification Window)**: Strict reuse of `DeliverySlotServiceImpl.acquireOrSwapLock`. The transition will verify the modification window (e.g., `placedAt + 15 mins`), then invoke the existing slot swap logic with the current lock ID. No parallel implementation.
- **(e) Cancel-within-window Scope**: Cancelling a `CONFIRMED` order transitions it to `CANCELLED` and releases the slot lock. **Refund logic is entirely out of scope for Phase 5**. No payment gateway refund calls will be made until Phase 6 (Returns/Refunds).

## 4. Test Strategy
- **State Transitions**: Parameterized unit tests on `Order` aggregate verifying valid jumps (`CONFIRMED` → `PACKED`, `PACKED` → `DISPATCHED`) and rejecting invalid jumps (`PAYMENT_PENDING` → `PACKED`).
- **WebSocket Broadcast**: Integration test proving a broadcast on `/topic/orders/{id}` reaches a subscribed client for that order, but not clients subscribed to different order IDs.
- **Modification Window Boundaries**: Unit/IT tests asserting reschedule/cancel operations succeed precisely at `placedAt + window - 1ms`, and fail with 409 at `placedAt + window + 1ms`.
- **Slot Swap Integrity**: Confirming `DeliverySlotLockingJpaIT` guarantees hold under the new reschedule caller.

## 5. API Contract Skeleton
```text
GET /orders/{id}/tracking
Response: { status: "DISPATCHED", driver: { name: "...", phone: "..." }, location: { lat: 12.3, lng: 45.6 }, updatedAt: "..." }

PUT /orders/{id}/status (Internal Webhook)
Header: X-API-Key: <secret>
Body: { status: "DISPATCHED", driverId: "...", lat: 12.3, lng: 45.6 }
Response: 200 OK

POST /orders/{id}/reschedule
Body: { newSlotId: <uuid>, slotDate: "YYYY-MM-DD" }
Response: 200 OK

POST /orders/{id}/cancel
Response: 200 OK

POST /orders/{id}/call-driver
Response: { status: "CALL_INITIATED" }
```

## 6. Pre-existing Codebase Status & Gaps
- **OrderStatus Enum**: `PACKED`, `DISPATCHED`, `DELIVERED`, `CANCELLED` exist but lack transition logic in `Order.java`.
- **Phase 4 Gap**: `Order` currently lacks a `placedAt` field (and `OrderEntity` does not map the DB `created_at` column). This will be patched at the start of Phase 5 implementation to support the modification-window logic.
- **Missing Infra**: No WebSocket, Driver entity, or CallProxy gateways exist yet.
