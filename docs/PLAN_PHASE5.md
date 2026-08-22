# Phase 5: Order Tracking & Delivery Consumption — Implementation Plan

## 1. Module Map
- **Modules**: `delivery-tracking` (new), `order` (existing).
- **Dependencies**: `delivery-tracking` consumes `order` state. `order` MUST NOT depend on `delivery-tracking` (acyclic). The core state machine (`pack()`, `dispatch()`, `deliver()`) lives in `order`.

## 2. Domain Model
- **New Entities**:
  - `DeliveryTrackingEvent`: Append-only audit log of tracking updates. Fields: `id`, `orderId`, `status`, `latitude` (nullable), `longitude` (nullable), `recordedAt`. Same shape as `LoginEvent`.
- **Order Aggregate Extensions**:
  - `placedAt`: Timestamp of creation/payment (Missing from Phase 4; to be mapped from DB `created_at` or added explicitly).
  - `driverId` / `driverPhone`: Plain nullable fields on `Order`, populated directly from the mock webhook payload. Explicitly flat scope; NO new `Driver` entity or aggregate, as no real delivery-partner system exists yet.
- **Ports**:
  - `CallProxyGateway`: Adapter port for driver-customer communication masking (Exotel/Knowlarity). Same shape as `PaymentGateway`. Stub implementation (`DummyCallProxyGateway`) initially.

## 3. Key Architecture Decisions
- **(a) WebSocket Auth/Scoping**: Per-order topic scoped by JWT claim at subscribe time. Spring WebSocket with STOMP will use a `ChannelInterceptor`. The initial WebSocket connection MUST pass the JWT via the STOMP CONNECT frame header ONLY (no URL query params, to prevent proxy/LB logging token leaks), verified by the existing `TokenValidator`. Upon `SUBSCRIBE /topic/orders/{id}`, the interceptor will verify the `userId` in the token matches the `Order.userId`.
- **(b) Polling Fallback (`GET /orders/{id}/tracking`)**: Simple direct DB read. Fetches the `Order` status and the latest `DeliveryTrackingEvent` row via a fast descending-sort query. No CQRS or separate read model is needed.
- **(c) Webhook Security (`PUT /orders/{id}/status`)**: Internal-only endpoint for delivery partners. Secured via `X-API-Key` header. This key MUST be sourced from an environment variable with NO usable fallback/default in `application.yaml` or `.env.example`. A misconfigured deployment must fail closed and reject all webhook calls rather than silently accept a placeholder.
- **(d) Reschedule Logic (Modification Window)**: Strict reuse of `DeliverySlotServiceImpl.acquireOrSwapLock`. The transition and lock swap MUST run inside a single `@Transactional` boundary — if the slot swap succeeds but the order update fails (or vice-versa), it rolls back atomically.
- **(e) Cancel-within-window Scope**: Cancelling a `CONFIRMED` order transitions it to `CANCELLED` and releases the slot lock. **Refund logic is entirely out of scope for Phase 5**. No payment gateway refund calls will be made until Phase 6 (Returns/Refunds).

## 4. Deviations from feature-doc endpoint shape
- Split `PUT /orders/{id}` into `POST /orders/{id}/reschedule` and `POST /orders/{id}/cancel` to avoid a single god-endpoint and make intent explicit.

## 5. Test Strategy
- **State Transitions**: Parameterized unit tests on `Order` aggregate verifying valid jumps (`CONFIRMED` → `PACKED`, `PACKED` → `DISPATCHED`) and rejecting invalid jumps.
- **WebSocket Broadcast**: Integration test proving a broadcast on `/topic/orders/{id}` reaches a subscribed client for that order, but not clients subscribed to different order IDs.
- **Modification Window Boundaries**: Unit/IT tests asserting reschedule/cancel operations succeed precisely at `placedAt + window - 1ms`, and fail with `MODIFICATION_WINDOW_EXPIRED` (mapped to 409) at `placedAt + window + 1ms`.
- **Slot Swap Integrity**: Confirming `DeliverySlotLockingJpaIT` guarantees hold under the new reschedule caller.

## 6. API Contract Skeleton
```text
GET /orders
Response: [ { id: <uuid>, status: "...", ... } ]  (New Phase 5 work)

GET /orders/{id}
Response: { id: <uuid>, status: "...", ... }      (Phase 4 gap, closed here)

GET /orders/{id}/tracking
Response: { status: "DISPATCHED", driver: { id: "...", phone: "..." }, location: { lat: 12.3, lng: 45.6 }, updatedAt: "..." }

PUT /orders/{id}/status (Internal Webhook)
Header: X-API-Key: <secret>
Body: { status: "DISPATCHED", driverId: "...", lat: 12.3, lng: 45.6 }
Response: 200 OK

POST /orders/{id}/reschedule
Body: { newSlotId: <uuid>, slotDate: "YYYY-MM-DD" }
Response: 200 OK

POST /orders/{id}/cancel
Response: 200 OK

POST /orders/{id}/reorder
Response: { cartId: <uuid>, message: "Items added to cart" } (New Phase 5 work)

POST /orders/{id}/call-driver
Response: { status: "CALL_INITIATED" }
```

## 7. Pre-existing Codebase Status & Gaps
- **OrderStatus Enum**: `PACKED`, `DISPATCHED`, `DELIVERED`, `CANCELLED` exist but lack transition logic in `Order.java`.
- **Phase 4 Gap 1 (placedAt)**: `Order` currently lacks a `placedAt` field (and `OrderEntity` does not map the DB `created_at` column). This will be patched at the start of Phase 5 implementation to support the modification-window logic.
- **Phase 4 Gap 2 (GET /orders/{id})**: Planned in Phase 4 but completely omitted. No controller endpoint or test stub exists. Will be implemented now. Does not include tracking data (that belongs to `/tracking`).
- **Missing Endpoints**: `GET /orders` and `POST /orders/{id}/reorder` do NOT exist. They are net-new additions for Phase 5.
- **Missing Infra**: No WebSocket or CallProxy gateways exist yet.
