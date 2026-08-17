# Housizy Customer Backend — Phase Plan

Java Spring Boot, modern monolith, mono-repo, Postgres (transactional) + MongoDB (document/catalog/logs) + Redis (ephemeral). Solo build with Claude Code assistance. B2B module deliberately sequenced last since it's separable.

**Infra additions (see INFRA.md in the repo for the authoritative phase-to-infra mapping):** Bloom filter and RabbitMQ are introduced in Phase 0; Elasticsearch is deliberately deferred to Phase 1, where search endpoints first exist. Status: **Phase 0 is complete** (SOLID-refactored, 37/37 tests passing) — verify Bloom filter/RabbitMQ landed in that phase before assuming they're still open work.

---

## Phase 0 — Foundations ✅ COMPLETE
**Modules touched:** project skeleton, `auth`, `user`
**Build:**
- Monorepo/module structure (Gradle multi-module or package-by-feature with ArchUnit boundary rules)
- Postgres + MongoDB + Redis connection config, base entities
- OTP auth (Redis-backed, TTL), JWT issuance/refresh, Google Sign-In, guest sessions
- User profile CRUD, device/session registry, logout-all-devices
- HSN/GST master table (Postgres) — seed with real government rate data early since Pricing depends on it
- **Bloom filter** (Redis-backed) in front of the phone-number-exists check on `/auth/otp/send`, to skip a Postgres round-trip for the common brand-new-number case — always falls back to the real DB check, never trusts a positive hit as certain
- **RabbitMQ** for OTP/SMS dispatch — send-OTP publishes a message instead of calling the SMS gateway inline, so the API responds fast and gateway failures don't fail the request; this queue/exchange gets reused by Notifications (Phase 7) and Tally sync retry later
**Done when:** Can register/login a user via OTP, issue/refresh JWT, and revoke sessions individually or all-at-once. Postgres/Mongo/Redis all reachable from a health-check endpoint. OTP send is bloom-filter-accelerated and queue-dispatched.
**Est:** ~1–1.5 weeks — **actual: complete**

## Phase 1 — Catalog & Search (read-side)
**Modules touched:** `catalog`, `search`
**Depends on:** Phase 0 (HSN master table for tax tagging)
**Build:**
- Product/category schema in MongoDB (variable attributes per category)
- Listing/detail APIs, multi-warehouse stock read (stock ledger itself can be a simple Postgres/Mongo placeholder here — full warehouse management is vendor-side, out of scope, but customer backend needs *a* read source)
- **Elasticsearch** introduced here (first phase with search endpoints) — index products on catalog write (Mongo → ES sync), power fuzzy matching, autocomplete, and the Hindi/English multilingual index; do not add the ES dependency any earlier than this
- Image search stub
- Reviews/Q&A, wishlist, back-in-stock subscriptions
**Done when:** Can browse categories, view a product detail page with correct HSN/GST tag, search with typo tolerance via Elasticsearch, and subscribe to back-in-stock.
**Est:** ~1.5–2 weeks

## Phase 2 — Pricing Engine
**Modules touched:** `pricing`
**Depends on:** Phase 1 (needs product/HSN data to price against)
**Build:**
- Core calculation service: base price → bulk tier → contract override → margin floor, in strict deterministic order
- GST resolution from HSN master table
- Coupon validation logic
- Unit tests per rule-combination — this is the highest-risk module, don't skip test coverage here
**Done when:** Given a SKU + qty + customer segment + optional coupon, returns a correct, fully-itemized price breakdown, with unit tests covering tier boundaries, coupon stacking, and margin-floor breach.
**Est:** ~1–1.5 weeks (small in code volume, but needs the most careful testing)

## Phase 3 — Cart, Checkout & Addresses
**Modules touched:** `cart`, `address`
**Depends on:** Phase 2 (every cart mutation re-runs pricing)
**Build:**
- Cart persistence (Postgres — transactional), multi-cart support (schema-ready, even if B2B UI comes later)
- Address CRUD, Google Maps geocoding, service-radius validation
- Delivery slot availability + locking
- Pre-payment re-validation (stock/price/slot re-check)
**Done when:** A cart can be built, priced live on every change, an address selected and validated, a slot locked, and a checkout summary returned — all before any payment integration exists.
**Est:** ~2 weeks

## Phase 4 — Payments & Order Core
**Modules touched:** `payment`, `order`
**Depends on:** Phase 3
**Build:**
- PhonePe integration: initiate, webhook (signature verify + idempotent processing), retry
- Order state machine (Postgres), idempotent order creation
- Order only confirms on webhook success, never client-reported
**Done when:** A real (sandbox) PhonePe payment can complete end-to-end and produce a `confirmed` order; a failed payment leaves the order retryable without duplication.
**Est:** ~2–2.5 weeks (PhonePe sandbox/webhook debugging is the time sink here, not the code itself)

## Phase 5 — Order Tracking & Delivery Consumption
**Modules touched:** `order` (extended), `delivery-tracking`
**Depends on:** Phase 4
**Build:**
- Delivery-partner webhook ingestion endpoint
- WebSocket broadcast to customer app + polling fallback
- Masked-proxy call initiation, tracking-link sharing
- Order modification window enforcement, reschedule/cancel
**Done when:** A mock delivery-partner webhook correctly updates order status and location, broadcasts live, and modification-window rules are server-enforced.
**Est:** ~1–1.5 weeks

## Phase 6 — Returns, Refunds & Invoicing
**Modules touched:** `returns`, `invoicing`
**Depends on:** Phase 4 (needs completed orders to return against)
**Build:**
- Return request + state machine, partial-quantity support
- Refund processing via payment gateway
- GST credit/debit note generation (sequential numbering — get this right immediately)
- GST-compliant invoice PDF generation on order confirmation
**Done when:** A delivered order can be returned (full or partial), refunded, and produces a correctly-numbered GST note; every confirmed order has a downloadable invoice.
**Est:** ~1.5–2 weeks

## Phase 7 — Notifications & Support
**Modules touched:** `notification`, `support`
**Depends on:** Phase 4+ (needs order events to notify about)
**Build:**
- Internal notify service abstracting push/SMS/WhatsApp behind one call, publishing to the **same RabbitMQ broker** set up in Phase 0 (new exchange/queues per channel, not a new broker)
- Cart abandonment job, back-in-stock trigger, order-event triggers
- WhatsApp Business API integration (submit templates to Meta early — approval lag)
- Support ticket API, chatbot NLU stub + escalation-to-human handoff
**Done when:** Order-state changes reliably trigger the right notification on the right channel; a support ticket can be created and escalated with context.
**Est:** ~1.5–2 weeks

## Phase 8 — Security, Compliance & Reliability Hardening
**Modules touched:** cross-cutting
**Depends on:** all prior phases existing to harden
**Build:**
- PII encryption at rest, schema/collection separation for sensitive fields
- RBAC enforcement audit across all endpoints (not just auth-gated, but scope-checked)
- Rate limiting on auth/search
- DPDP export/deletion jobs
- Idempotency-key enforcement audit across order/payment endpoints
**Done when:** A security pass confirms no endpoint leaks PII beyond its intended scope, DPDP export/delete works end-to-end, and retrying any mutating request is safe.
**Est:** ~1–1.5 weeks

## Phase 9 — B2B Module (separable — ship as v1.1)
**Modules touched:** `b2b` (roles, RFQ, PO, approvals, statements)
**Depends on:** Phases 2–4 (pricing, cart, orders) since it extends them
**Build:**
- Multi-user company accounts + role/site scoping
- RFQ routing + quote comparison + expiry
- PO upload/bulk parsing
- Approval workflow engine (thresholds, escalation, delegation)
- Monthly consolidated statement generation job
**Done when:** A company account with multiple roles can submit an RFQ, convert it to an order, have it go through approval, and generate a correct monthly statement.
**Est:** ~2.5–3 weeks

## Phase 10 — Testing, Load Testing & Final Hardening
**Build:**
- End-to-end test pass across the whole order lifecycle
- Load test cart/checkout/payment paths specifically (the 60-min SLA path)
- Fix whatever breaks
**Est:** ~1–1.5 weeks

---

## Totals
- **Phases 0–8 (B2C-complete backend):** ~12–15 weeks
- **+ Phase 9 (B2B):** +2.5–3 weeks
- **+ Phase 10 (hardening):** +1–1.5 weeks
- **Grand total:** ~16–19.5 weeks (~4–5 months) solo with Claude Code, matching the earlier estimate

## Key sequencing rules (don't reorder these)
- Pricing (Phase 2) must exist before Cart (Phase 3) — cart trusts nothing it can't re-price live
- Payments must confirm orders via webhook only (Phase 4) — this shapes the order state machine, don't build order states before payment webhook logic is settled
- HSN/GST master data (Phase 0) must be seeded before Catalog (Phase 1) tags products against it
- B2B (Phase 9) extends Cart/Orders/Pricing rather than replacing them — building it earlier means rebuilding it once those stabilize
