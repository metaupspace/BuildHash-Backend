# BuildDash Backend — Progress

## Status: Phase 5 (Order Tracking & Delivery Consumption) — COMPLETE, 326/326 tests green

All deliverables across Checkpoints A, B, and C are fully implemented, reviewed, and proven green with 326/326 tests passing.

---

## Status: Phase 5, Checkpoint C (Reschedule, Cancel-within-Window, Call-Driver) — COMPLETE, 326/326 tests green

Per PLAN_PHASE5.md. Full review and hardening of reschedule (`POST /orders/{id}/reschedule`), cancel-within-window (`POST /orders/{id}/cancel`), and call-driver (`POST /orders/{id}/call-driver`).

**1. Scope & Retroactive Review:**
- Code for reschedule, cancel-within-window, and call-driver originally existed on disk from the untraced build discussed in Checkpoint B's entry. It was explicitly scoped out of B, audited here under strict TDD, and hardened with concurrency proofs and boundary checks before acceptance.

**2. Key Architectural Finding (`acquireOrSwapLock` Blindness):**
- `DeliverySlotServiceImpl.acquireOrSwapLock` could NOT be directly reused for reschedule as `PLAN_PHASE5.md`'s decision (d) originally called for. `acquireOrSwapLock` is keyed to `findActiveByUserId`, but an already-paid order's delivery slot lock has status `CONSUMED`, not `ACTIVE`. Reusing `acquireOrSwapLock` directly would have failed to find the consumed lock, silently leaking slot capacity by not decrementing the old slot's counter.
- **Resolution**: Added two dedicated sibling methods to `DeliverySlotServiceImpl` — `swapConsumedLock` and `releaseConsumedLock` — centralizing the exact same race-safe `SELECT ... FOR UPDATE` counter discipline without polluting or breaking the contract of `acquireOrSwapLock`. This stands as a necessary architectural revision based on runtime lock lifecycle state, not a compromise of design.

**3. Reschedule Fixes:**
- Switched from hand-rolled inline repository calls to `deliverySlotService.swapConsumedLock`.
- Applied pessimistic row-locking (`orderRepository.findByIdForUpdate`) with a post-lock status recheck to guard against lost updates and race conditions.
- Confirmed single `@Transactional` boundary across order update and slot swap, ensuring both commit or roll back atomically together.
- Removed an invented, hardcoded 30-day lock TTL after auditing the entire codebase and confirming zero queries or jobs inspect `expiresAt` on `CONSUMED` locks.

**4. Cancel-within-Window Fixes:**
- Switched to `deliverySlotService.releaseConsumedLock`; applied row-locking (`findByIdForUpdate`) with post-lock status rechecks.
- **Guard Scope Kept `CONFIRMED`-Only**: Rejection of widening customer self-cancel to `PACKED`. Once `PACKED`, cancellation is strictly a warehouse-side action via the delivery partner webhook's `cancelFromDelivery()`. Widening customer cancel to `PACKED` would create duplicate cancel paths with conflicting side effects (releasing vs not releasing slot capacity).
- Confirmed via full-repo audit: zero refund logic exists in this path, correctly deferred to Phase 6.

**5. Call-Driver Fixes:**
- `DummyCallProxyGatewayAdapter` was missing `@Profile("!prod")` (a violation of the project's dev-stub hardening convention seen in `DummyPaymentGatewayAdapter`, `SmsOtpSender`, etc.). Added `@Profile("!prod")`.
- Added missing order-not-found and non-owner 404 test coverage (`callDriver_whenNotOwner_returnsNotFound`, `callDriver_whenOrderNotFound_returnsNotFound`), aligning with `cancel` and `getOrder` error semantics.

**6. New Concurrency Proofs (`DeliverySlotLockingJpaIT`):**
- `concurrentSwapConsumedLock_enforcesTargetCapacityUnderRace`: Proves 10 concurrent threads racing to swap into a capacity-2 target slot results in exactly 2 successes and 8 `SlotUnavailableException` rejections, with the old slot counter decremented by exactly 2 (10 -> 8).
- `concurrentReleaseConsumedLock_atomicCounterDecrementUnderRace`: Proves 5 concurrent releases against the same counter row decrement atomically to 0 without lost updates or double-decrements.
- Confirmed `swapConsumedLock` and `releaseConsumedLock` use default `REQUIRED` transaction propagation (joining the caller's transaction); `REQUIRES_NEW` was explicitly verified absent to preserve cross-entity atomicity.

**7. Test Count:**
- 320 (end of Checkpoint B fixes) → 326 (after Checkpoint C fixes, race tests, and edge case coverage). All 326/326 tests green with 0 failures, 0 errors, 0 skipped.
- Original `DeliverySlotLockingJpaIT` guarantees confirmed unaffected throughout.

### Next Phase
Phase 6 — Returns, Refunds & Invoicing, per `docs/builddash-backend-phase-plan.md`.

---

## Status: Phase 5, Checkpoint B (Delivery Status, Webhook Ingestion, WebSocket Broadcast) — COMPLETE (reviewed scope), 320/320 tests green. Checkpoint C OPEN.

Per PLAN_PHASE5.md. Checkpoint B's reviewed, approved scope is complete; Checkpoint C is explicitly open and inherits pre-existing-but-unreviewed code (details below).
- `driverId`/`driverPhone` added to Order as plain nullable fields.
- `GET /orders`, `GET /orders/{id}` built — non-owner returns 404 (not 403), matching the `DeviceController` precedent.

**Reorder design (`POST /orders/{id}/reorder`):**
- Final design bypasses the persistent cart entirely. Builds an in-memory-then-persisted `REORDER_SCRATCH` cart (own `cart_type`, not a repurposed `project_id`), re-prices via `CartPricingCalculator`, returns a real `cartId` the client hands to `POST /checkout/intent`.
- Two earlier wrong designs rejected: 1) Overwrite-the-primary-cart (rejected because it destroys user's current WIP cart). 2) `project_id`-as-isolation-key (rejected because `project_id` isn't meant for cart lifecycle isolation). Useful context for future maintainers.

**Tracking & refactoring:**
- `DeliveryTrackingEvent` (schema/domain/port/adapter only, no producer yet — that's Checkpoint B).
- Self-invocation `@Transactional` audit: zero live bugs found. `retryPayment`'s sequential `transactionTemplate.execute()` -> gateway-call-after-commit pattern explicitly traced and confirmed safe (not `registerSynchronization(afterCommit)` as originally assumed — noted this correction).
- `CartPricingCalculatorImpl.calculate()` refactored from cognitive complexity 30 to ~6. Extracted `priceItem`, `evaluateCouponRules`, `computeDiscountAmount` as plain (non-transactional) private methods.

**Guest-cart fixes:**
- Guest-cart FK bug: `guestSession()` previously issued tokens with no backing `users` row, making any guest cart write fail with a FK violation. Fixed by persisting a real (flagged) `users` row on guest-session creation, plus a full merge-on-login flow (merge line items into an existing cart, or reassign if none exists; guest token invalidated post-merge) triggered via OTP verify.
- Guest-write security regression: an interim `SecurityConfig` change blocked ALL guest mutations, contradicting the documented guest-cart-mutation-allowed/order-placement-blocked design. Fixed with an explicit `/cart/**` carve-out above the general lockdown.

### Next
~~Phase 5 Checkpoint B~~ — completed above (see Checkpoint B entry).

---

## Status: Phase 3 (Checkout Intent) — Checkpoint C COMPLETE, 141/141 tests green

The `product_base_prices` write-path gap flagged at the end of Checkpoint C is closed: your
call was to add the write path now rather than defer it like `margin_rules`.
- `ProductBasePriceRepository` gained `BigDecimal save(UUID productId, BigDecimal price)`;
  `ProductBasePriceRepositoryAdapter` implements it as a plain entity-build-and-save (same
  shape as every other simple-save adapter in this codebase, e.g. `DeviceRepositoryAdapter`)
  — no dedicated unit test added for it, matching the existing convention that trivial
  entity-mapping saves aren't independently unit-tested (their coverage comes from whatever
  exercises the calling code, here `CatalogSeeder` running under `AbstractIntegrationTest`'s
  dev-profile equivalent paths and the manual seeded-data smoke path).
- `CatalogSeeder` (`@Profile("dev")`) now calls `productBasePriceRepository.save(...)` right
  after each seeded product is saved, with real prices (₹380/₹360/₹899 for the three seed
  products) — so `/pricing` is actually exercisable against seeded data without a manual SQL
  insert, closing the exact gap the Checkpoint C review surfaced.
- Deliberately did **not** touch `margin_rules`' manually-seeded-only status — that gap is
  categorically different (optional data, most products legitimately have none) and wasn't
  part of what you asked to close here.

141/141 unchanged by this fix (no test exercises `CatalogSeeder` directly — it's dev-profile
seed data, not app logic under test; compile + the full pre-existing suite is the verification
here). All 8 PLAN_PHASE2.md deliverables are now built with no outstanding gaps:
module map, domain model, precedence engine, bulk tiers/contract pricing (with a two-layer
overlap defense, proven at both layers), coupon scope (in-scope vs. Phase-3-deferred, stated
explicitly), test strategy (102 pre-existing + 39 new, bounded-cross-product interpretation
stated as a deliberate decision, not a silent narrowing), API contract (internal-only, per the
tax-section precedent), and all four originally-open design decisions resolved and recorded
above. See PLAN_PHASE1.md-style close-out precedent — this file's Checkpoint A/B/C entries
below are the checkpoint-by-checkpoint detail; nothing here is deferred without an explicit
note saying so and why.

### Next phase

Phase 3 (Cart) — not started, not scoped in this session. Known forward references from this
phase: minimum-order-value and multi-coupon-stacking coupon rules (deferred here, need
cart-level aggregation), `contract_pricing.user_id` will need a rekeying migration once Phase
9's company-account model exists.

---

## Status: Phase 2, Checkpoint C (pricing tests) — DONE, 141/141 tests green (39 new)

Per PLAN_PHASE2.md Section 6. 141/141 = the pre-existing 102 plus 39 new: 23 in
`PricingStepsTest`, 7 in `PricingCalculatorImplTest`, 6 in `ContractPriceRepositoryAdapterTest`,
3 in `ContractPriceOverlapJpaIT` (real Testcontainers Postgres — the only new test needing one).

**Two cases moved to the layer that actually implements them, not the layer originally
proposed** — found while writing the tests, not before:
1. Contract-price expired/not-yet-effective — the Checkpoint B proposal listed these under
   `PricingStepsTest#applyContractOverride`, but that resolution happens in
   `ContractPriceRepositoryAdapter.findActive` (a plain-Java filter over an already-fetched
   list), not in the step — by the time `PricingContext.activeContractPrice()` reaches
   `applyContractOverride`, it's already either "the currently active one" or `null`. The step
   itself only has one real branch (present vs. absent), tested as such. Expired/not-yet-
   effective/open-ended-window now live in the new `ContractPriceRepositoryAdapterTest`
   (Mockito, mocks `ContractPriceJpaRepository` — no DB needed, the filter is pure Java).
2. Margin-rule product-vs-category fallback (your explicit add) — lives in
   `PricingCalculatorImpl.loadContext`'s `.or(...)` chain, not in `applyMarginFloor` (which
   only ever sees the already-resolved `MarginRule`, with no way to tell where it came from).
   Tested in `PricingCalculatorImplTest.calculate_marginRule_fallsBackToCategoryLevel...`
   (mocks `findByProductId` empty + `findByCategoryId` present) — distinct from
   `calculate_noRuleAtEitherLevel_...` (both empty), so the fallback path and the true
   no-rule-anywhere path can't silently collapse into the same green checkmark.

**The malformed-margin-rule WARN log itself is not asserted** — `PricingSteps
Test#applyMarginFloor_malformedRule_passesThroughWithoutThrowing` verifies the *behavior*
(passthrough, no exception) but not the log line, since asserting on SLF4J output needs a
test log appender this codebase has no existing pattern for. Flagging the gap rather than
quietly asserting less than the name implies.

**DB-level proof** (`ContractPriceOverlapJpaIT`): goes through `ContractPriceJpaRepository`
directly, not the port/adapter — genuinely bypasses the application-level overlap check.
`rawConcurrentInsert_bypassingTheAdapter_isRejectedByTheDbExclusionConstraint` inserts one row,
then a second overlapping row straight via the JPA repository, and asserts
`excl_contract_pricing_no_overlap` throws `DataIntegrityViolationException` — this is the test
that would fail if the exclusion constraint were ever silently dropped or misconfigured. A
second test proves non-overlapping windows for the same user+product both commit fine (the
constraint isn't over-broad), and a third exercises the adapter's translation of that same DB
failure into `ContractPriceOverlapException` end-to-end against real Postgres, not mocked.

### Checkpoint C file list (as built)

- `test/.../application/impl/PricingStepsTest.java` — 23 cases across all 5 steps, plus a
  package-private `ContextBuilder` test helper (same judgment as `PricingSteps.Result` in
  production: a 10-field record used with only 2-3 fields varying per call site).
- `test/.../application/impl/PricingCalculatorImplTest.java` — Mockito unit test, mocks all 9
  ports; `@BeforeEach` stubs are `lenient()` since the two fail-fast guard tests
  (product-not-found, no-base-price) short-circuit before some of them are ever reached.
- `test/.../infra/persistence/adapter/ContractPriceRepositoryAdapterTest.java` — Mockito,
  `findActive` filtering + application-level `save()` overlap rejection.
- `test/.../infra/persistence/adapter/ContractPriceOverlapJpaIT.java` — real Postgres, the
  DB-level exclusion-constraint proof.

### Phase 2 status — not marking COMPLETE yet, one gap surfaced during close-out review

All 8 PLAN_PHASE2.md deliverables (module map, domain model, precedence engine, bulk
tiers/contract pricing, coupon scope, test strategy, API contract, open questions) are built
and match the locked decisions. But: **no write path exists anywhere for
`product_base_prices`.** `ProductBasePriceRepository` only has `findByProductId` (Checkpoint A,
matching the "manually seeded" precedent set for `margin_rules`) — but unlike margin rules
(optional, most products legitimately have none), a missing base price isn't optional: every
product needs one to be priced at all, and `calculate()` fail-closed throws
`ProductNotPricedException` for any product without one. Right now that means every product
`CatalogSeeder` creates is unpriceable through the running system — the only way to populate
`product_base_prices` today is a manual SQL insert. This was never explicitly decided (Section
4's locked decisions covered *where* base price lives, not *how it gets written*) — flagging
it now rather than letting Phase 2 read as fully closed while pricing is practically unusable
without hand-written SQL. Needs your call: add a `save()` to the port + seed it via
`CatalogSeeder` (dev/test usability) now, or deliberately defer to whichever phase adds admin
tooling, same as `margin_rules`.

---

## Status: Phase 2, Checkpoint B (pricing pipeline) — DONE, 102/102 tests green

Per PLAN_PHASE2.md Section 3. `PricingCalculator`/`PricingCalculatorImpl` + `PricingSteps`
built; no tests yet (`@ParameterizedTest` suite is Checkpoint C, so this checkpoint's logic is
unexercised until then — verified by compile + the unchanged 102/102 pre-existing suite only).

**Purity actually upheld, not just named that way:** every step has the same signature —
`static PriceCalculationResult applyX(PriceCalculationResult running, PricingContext ctx)` —
for `applyBulkTier`, `applyContractOverride`, `applyCoupon`, `applyMarginFloor`, `applyGst`
(`PricingSteps.java`). `PricingContext` (a record) is built exactly once, in
`PricingCalculatorImpl.loadContext`, where every repository/port call for the whole
calculation happens up front — product, base price, category, bulk tiers, active contract
price, coupon lookup + redemption count, margin rule (product-level, falling back to
category-level), GST rate. No step calls a port. One caveat, stated plainly rather than
glossed over: `applyMarginFloor` calls `Logger.warn(...)` on the malformed-rule pass-through
path (per your ask below) — a side effect, so not zero-side-effect, but still zero I/O to a
repository/port and fully deterministic for a given `(running, ctx)` pair. That's the actual
purity guarantee here: no hidden DB/network calls inside a step, not "no side effects at all."

**Margin-floor pass-through, now observable:** `ctx.marginRule() == null` (no rule configured
for the product or its category — the common case, since margin data is manually seeded)
passes through silently, no log — that's normal, not a gap. A rule that *exists* but has
neither `floorPrice` nor a usable `costPrice`+`floorPercent` pair is genuinely malformed data;
that path logs `WARN` with the rule id and product id before passing through uncapped. This
makes a bad manually-seeded `margin_rules` row operationally visible (grep the logs) instead
of silently invisible, without failing the whole calculation the way missing-base-price/
unresolved-GST do — a malformed margin rule is a business-integrity gap (could mean selling
below cost undetected), not a hard data-missing case.

**Coupon validation** (`PricingSteps.applyCoupon`) reuses the existing exception hierarchy
with the four codes from PLAN_PHASE2.md Section 5 — no dedicated `CouponInvalidException`:
`NotFoundException("COUPON_NOT_FOUND", ...)` (covers both "code doesn't exist" and "coupon
inactive" — an inactive coupon is treated as not existing for redemption purposes, not a fifth
code), `BadRequestException` for `COUPON_EXPIRED`/`COUPON_USAGE_LIMIT_REACHED`/
`COUPON_CATEGORY_INELIGIBLE`. Discount computed against `contractAdjustedTotal` (post-tier,
post-contract-override, pre-margin-floor), capped at that total so a coupon can't push the
line item negative before the floor check even runs.

**Margin floor folds the coupon discount in before comparing to the floor**
(`preFloorPrice = contractAdjustedTotal - couponDiscountAmount`) — the floor exists to stop a
product ever selling below cost, and a coupon discount is exactly the kind of reduction that
could push a price below cost, so it has to be inside the check, not applied after.

**Product-not-found guard added, not in the plan's text but an obvious gap:** `calculate()`
throws `NotFoundException("PRODUCT_NOT_FOUND", ...)` for an unknown `productId`, same code and
pattern already used by `CatalogServiceImpl.getDetail`/`ReviewServiceImpl`/etc. Category
resolution for a product, by contrast, follows `CatalogServiceImpl.getDetail`'s existing
soft-fail precedent (`.orElse(null)`) — a missing category only affects the margin-rule
category-fallback and coupon eligible-category check, both already null-safe, so there's no
reason to hard-fail pricing over it the way missing-base-price/GST do.

### Checkpoint B file list (as built)

- `domain/exception`: `ProductNotPricedException`, `GstRateUnresolvedException` (carries the
  unresolved `hsnCode`) — both extend `NotFoundException`.
- `domain/model/PricingRequest.java` (record: productId, quantity, nullable userId, nullable
  couponCode).
- `domain/model/PriceCalculationResult.java` — added a static `initial(request, hsnCode,
  basePrice)` factory (Checkpoint A only defined the record shape).
- `application/service/PricingCalculator.java` — `calculate(PricingRequest):
  PriceCalculationResult`.
- `application/impl/PricingContext.java` — package-private record, the pre-resolved read-only
  snapshot every step consumes.
- `application/impl/PricingStep.java` — package-private functional interface,
  `(PriceCalculationResult, PricingContext) -> PriceCalculationResult`.
- `application/impl/PricingSteps.java` — the five pure step functions + a package-private
  `Result` mutable copy-builder (seeded from the running record, never crosses a step
  boundary as mutable state) used to avoid a 21-argument constructor call at every step.
- `application/impl/PricingCalculatorImpl.java` — composes `ProductRepository`,
  `CategoryRepository`, `HsnGstRateRepository` (existing, read-only) + `ProductBasePriceRepository`,
  `BulkPricingTierRepository`, `ContractPriceRepository`, `CouponRepository`,
  `CouponRedemptionRepository`, `MarginRuleRepository` (Checkpoint A).

### Next

Checkpoint C (tests): `@ParameterizedTest`/`@MethodSource` suite per step (bulk tier boundary
conditions, contract present/expired/not-yet-effective, coupon valid/expired/limit-reached/
ineligible, margin floor triggered/not, GST resolved/unresolved, base price present/missing),
3-4 composed end-to-end combination tests, and a JpaIT proving the DB-level exclusion
constraint rejects a raw concurrent insert that bypasses the adapter's own check. File list to
be proposed before writing.

---

## Status: Phase 2, Checkpoint A (pricing schema + domain/persistence) — DONE, 102/102 tests green

Per PLAN_PHASE2.md. Schema, domain models/ports, and the full persistence layer for the
pricing engine — no pipeline logic yet (`PricingCalculator`, `PricingSteps` are Checkpoint B)
and no new tests yet (the `@ParameterizedTest` suite + overlap-rejection proof are
Checkpoint C). 102/102 is the pre-existing Phase 1 suite, unaffected by this checkpoint.

**Two conventions corrected vs. PLAN_PHASE2.md's literal text**, found only once the actual
codebase was checked during implementation (the plan was written from the phase/feature docs,
not a byte-for-byte code read):
1. `coupons.eligible_category_ids`: plan said `UUID[]`. Codebase has zero native-array usage
   anywhere — `Product.attributes`/`images`/`stock` and `Category.attributeSchema` all use
   `@JdbcTypeCode(SqlTypes.JSON)` on a JSONB column. Built as JSONB `List<UUID>` instead, same
   as every other collection column in the schema.
2. `coupons.discount_type`: plan said plain `String`. Codebase convention for a fixed set of
   values is a `domain/enums` type + `@Enumerated(EnumType.STRING)` (see `ProductStatus` on
   `Product`/`ProductEntity`). Added `domain/enums/DiscountType {PERCENT, FLAT}`.

**Exception count trimmed from 4 to 3** vs. the plan's module map: `MarginFloorBreachException`
and `CouponInvalidException` were never actually thrown anywhere in the plan's own Sections 3/5
— margin floor *adjusts* the price rather than rejecting it, and coupon rejections reuse the
existing `BadRequestException`/`NotFoundException` base classes with specific codes
(`COUPON_EXPIRED` etc., added in Checkpoint B). Building the two unused subclasses now would be
dead code sitting unused until Checkpoint B, so only `ContractPriceOverlapException` (used here)
exists so far; `ProductNotPricedException`/`GstRateUnresolvedException` land in Checkpoint B
where the pipeline actually throws them.

**Contract-price overlap rejection — enforced twice, deliberately** (your explicit ask this
session: prove it's race-safe, not just documented as intent):
- **Application layer** (`ContractPriceRepositoryAdapter.save`): fetches the small existing
  `user_id`+`product_id` row set via `ContractPriceJpaRepository.findByUserIdAndProductId`,
  checks each for `[effectiveFrom, effectiveTo)` interval overlap in plain Java (`null
  effectiveTo` = open-ended/unbounded), throws `ContractPriceOverlapException` before ever
  calling `save`. This is the normal-path check — clear domain error, no DB round-trip surprises.
- **DB layer, the actual concurrency backstop** (`V7__pricing_schema.sql`):
  `excl_contract_pricing_no_overlap`, a GiST `EXCLUDE` constraint on
  `(user_id WITH =, product_id WITH =, tstzrange(effective_from, effective_to) WITH &&)`,
  backed by `btree_gist` (bundled in the official `postgres:16-alpine` image's contrib modules,
  so no image change needed). Two concurrent inserts can both pass the application-level check
  above before either commits — only the DB-level exclusion constraint, checked atomically at
  statement-execution time, actually prevents both from landing. `saveAndFlush` (not `save`) is
  used specifically so that check happens synchronously inside the adapter method — with a
  lazy/batched flush at end-of-transaction instead, the resulting
  `DataIntegrityViolationException` would surface far from this method's `catch` block as a raw
  Spring exception, not the same `ContractPriceOverlapException` the application-level path
  throws. Rejected alternative: `SERIALIZABLE` transaction isolation — would also close the
  race, but forces retry-loop handling on every pricing write for a guarantee the exclusion
  constraint gives for free at the schema level.
- Checkpoint C will add the JpaIT that proves the DB layer actually rejects a raw concurrent
  insert that bypasses the adapter (inserting directly via a second `EntityManager`/native SQL),
  not just that the adapter's own Java-level check works.

### Checkpoint A file list (as built)

- `db/migration/postgresql/V7__pricing_schema.sql`: `product_base_prices`,
  `bulk_pricing_tiers`, `contract_pricing` (+ `excl_contract_pricing_no_overlap`), `coupons`,
  `coupon_redemptions`, `margin_rules`.
- `domain/enums/DiscountType.java`.
- `domain/model`: `BulkPricingTier`, `ContractPrice`, `Coupon`, `CouponRedemption`,
  `MarginRule` (mutable POJO style, matching `HsnGstRate`/`Product`), `PriceCalculationResult`
  (immutable `record` — computed pipeline output, not persisted state; used starting
  Checkpoint B).
- `domain/exception/ContractPriceOverlapException.java` (extends `BadRequestException`).
- `domain/port`: `ProductBasePriceRepository`, `BulkPricingTierRepository`,
  `ContractPriceRepository`, `CouponRepository`, `CouponRedemptionRepository` (read-only, no
  `save` — per the locked Phase 2 decision), `MarginRuleRepository` (product-level +
  category-level fallback finder).
- `infra/persistence/entity`: `ProductBasePriceEntity`, `BulkPricingTierEntity`,
  `ContractPriceEntity`, `CouponEntity`, `CouponRedemptionEntity`, `MarginRuleEntity`.
- `infra/persistence/repository`: matching `*JpaRepository` interfaces, public, per the
  kind-based layout's standing trade-off.
- `infra/persistence/mapper`: `BulkPricingTierMapper`, `ContractPriceMapper` (only mapper here
  with `toEntity`, since it's the only pricing type this checkpoint writes), `CouponMapper`,
  `MarginRuleMapper`. No `ProductBasePriceMapper` (port returns raw `BigDecimal`, no domain
  type to map to) and no `CouponRedemptionMapper` (port only returns a count, no entity
  marshaling needed).
- `infra/persistence/adapter`: matching `*RepositoryAdapter` classes, package-private.

### Next

Checkpoint B (pipeline): `PricingSteps` pure functions, `PricingCalculator` port +
`PricingCalculatorImpl`, `ProductNotPricedException`/`GstRateUnresolvedException` wired in,
coupon validation logic. File list to be proposed before writing.

---

## Status: Phase 1 — COMPLETE (Waves 1–3 all done, 102/102 tests green)

All three waves of Phase 1 (PLAN_PHASE1.md) are done: Wave 1 (catalog listing/detail), Wave 2
(review/qna/wishlist/backinstock), Wave 3 (outbox + search, checkpoints 3a/3b/3c). See
PLAN_PHASE1.md's own close-out note for the phase-level summary and what's explicitly
deferred to Phase 2+. Checkpoint-by-checkpoint detail for Wave 3 follows below; Waves 1-2
detail is further down this file.

## Status: Phase 1, Wave 3, Checkpoint 3c (search API + reindex) — DONE, 102/102 tests green

Last checkpoint of Wave 3, and of Phase 1. `/search` now actually returns results (once 3c's
own reindex job bootstraps the `products` alias 3b's listener had nothing to write to yet),
and the blue-green reindex mechanism both keeps that alias current and closes the outbox
lifecycle 3a/3b left open (`PUBLISHED` rows now actually reach `PROCESSED`).

**Three deliberate simplifications, flagged before building, not discovered after:**
1. `/search/trending` — one `search_queries` Postgres log table + a lazy-refreshed Redis
   cache (compute on miss, 10min TTL), instead of PLAN_PHASE1.md's literal "background job
   aggregating query volume." Same end behavior, one fewer scheduled job.
2. `/search/suggest` — a light Redis cache (60s TTL, keyed by `lang:prefix`), same
   `RedisOtpStore`-style pattern already in the codebase — not full "hot query" tracking.
3. `/search/image` — genuinely a stub per the plan: `ImageSearchProvider` port +
   `StubImageSearchProvider` returning zero matches. Not `@Profile`-gated like `SmsOtpSender`
   — no real vendor is expected imminently even in prod (Open Question #2).

**A real, plan-inherited inconsistency, documented rather than silently worked around:**
`SearchQueryGateway.search`'s `category` filter is a **name** (e.g. `"Cement"`), not a UUID —
Section 2's ES mapping only ever indexes `ProductSyncPayload.category` (the name), never a
categoryId field. `GET /products?category=` (Postgres-backed) and `GET /search?category=`
(ES-backed) therefore filter by genuinely different representations of "category." Not a
bug to fix here — the ES document simply doesn't have an id field to filter by.

**SecurityConfig:** all of `/search/**` (every HTTP method, not just GET) is now `permitAll`
— unlike `/products/**`, nothing under `/search/**` is ever a mutation, so it doesn't need
that path's GET-only carve-out. `/users/me/search-history` needed no change (already covered
by the existing `/users/**` → `hasRole("USER")` matcher).

**Blue-green reindex** (`application/impl/CatalogReindexer`, `@Scheduled` cron, gated by the
existing `SchedulingConfig("!test")`, per Open Question #7 — cron-only for now, a future
admin-triggered variant can call the same `reindex()` method without this class changing):
create a new index (`domain/port/SearchIndexAdmin`, real adapter builds the full Section-2
mapping — name + `name.autocomplete` edge-ngram + `name.hi` Hindi analyzer, category/brand
keywords, flattened attributes) → paginate **all** `ACTIVE` products via the existing
`ProductRepository.findPage` keyset cursor (the exact same hasNext pattern `ProductReader.
list` already uses — one paging implementation, not two) → build each payload via 3a's
`ProductSyncProjectionBuilder` (one code path, not two, per Section 3) → write into the new
index only, never the alias → atomic alias swap → **reconciliation sweep**: any outbox row
still `PENDING`/`PUBLISHED` with `createdAt` before this run started gets `markProcessed` —
safe because the backfill that just ran re-derived that row's product state from the same
Postgres source of truth; rows created during the run are left for the next cycle, not raced
against.

**Your required test, proven structurally rather than by timing/threading**
(`CatalogReindexerJpaIT.reindex_oldIndexUntouchedDuringBackfill_...`): asserts the **old
index's document count never changes** during/after a reindex run (the backfill provably
never writes to it — only ever to the new index name), the alias only resolves to the new
index *after* `reindex()` returns, and the new index's content is independently verified
against Postgres. This proves "old alias serves until new index is ready" deterministically
— anyone reading via the alias throughout the entire backfill window would have seen the old
index, complete and untouched, right up until the swap. No flaky race simulation needed to
demonstrate the same real guarantee. A second test proves the reconciliation sweep flips a
predating `PUBLISHED` row to `PROCESSED`.

**Found and fixed during verification, not anticipated in the file-list proposal:** two bugs
in the reindex test itself (not production code) — the FK-violating `UUID.randomUUID()`
placeholder product id on the reconciliation test's outbox row (fixed: use the real saved
product's id), and a hardcoded `== 2` document-count assertion that failed once other ITs in
the same suite run had already added their own products to the shared Testcontainers
Postgres before this test ran (fixed: `>= 2` — the reindexer correctly picking up every
product in the database, not just this test's own two, is the correct production behavior,
not something to constrain away).

### Checkpoint 3c file list (as built)

- `db/migration/postgresql/V6__search_queries.sql`: `search_queries` (nullable `user_id` FK,
  `query_text`, `lang`, `created_at`) — one table serves both per-user history and the
  trending aggregate.
- `domain/model`: `ProductSearchHit`, `SearchQueryLogEntry`, `TrendingQueryCount`.
- `domain/port`: `SearchIndexAdmin`, `SearchQueryGateway`, `SearchQueryLogRepository`,
  `ImageSearchProvider`.
- `infra/search`: `SearchQueryBuilder` (concrete, no interface, per your decision),
  `ElasticsearchSearchIndexAdminAdapter`, `ElasticsearchSearchQueryGatewayAdapter`.
- `infra/external`: `StubImageSearchProvider`.
- `infra/persistence`: `entity/SearchQueryLogEntity`, `repository/SearchQueryLogJpaRepository`,
  `mapper/SearchQueryLogMapper`, `adapter/SearchQueryLogRepositoryAdapter` — built directly
  into the kind-based layout (see the persistence-layout note above), not the flat one.
- `application/impl`: `SearchServiceImpl` (no interface — single caller, same judgment as
  `OtpSendService`), `CatalogReindexer`.
- `api/controller`: `SearchController` (`/search`, `/search/suggest`, `/search/trending`,
  `/search/image`), `SearchHistoryController` (`/users/me/search-history`).
- `api/dto/response`: `ProductSearchHitResponse`, `SearchResultResponse`, `SuggestResponse`,
  `TrendingResponse`, `ImageSearchResponse`, `SearchHistoryEntryResponse`.
- `api/mapper/SearchMapper`.
- `infra/config/SecurityConfig` (touched — `/search/**` permitAll).

### Next

Phase 2 (Pricing & Tax, per PLAN_PHASE1.md's own forward references) — not started, not
scoped in this session.

---

**Persistence layout, second occurrence (2026-08-18, before Checkpoint 3c started):** the
same external kind-based restructure from 3a's recovery note (below) reapplied itself to all
of `infra/persistence`, including the 3a/3b files added since. This time, kept intentionally
per explicit instruction rather than reverted — fixed the 44 broken cross-package visibility
references (`*JpaRepository`/`*Mapper` widened to `public`) instead. This is now the
standing layout; see the "Final architecture snapshot" section for the updated tree and the
accepted encapsulation trade-off. Checkpoint 3c's new persistence files land directly in
`entity/`/`mapper/`/`repository/`/`adapter/`, not the flat layout.

## Status: Phase 1, Wave 3, Checkpoint 3b (ES sync) — DONE, 90/90 tests green

First real Elasticsearch touchpoint in the codebase. `catalog.product.changed` now has a
consumer; a product write is eventually searchable, with ordering/idempotency handled by
external versioning instead of app-side compare-and-swap.

**New dependency + infra:**
- `../build.gradle`: `spring-boot-starter-data-elasticsearch` — used only for its
  auto-configured `ElasticsearchClient`/`RestClient` beans (off `spring.elasticsearch.uris`).
  Deliberately **not** using Spring Data's `ElasticsearchRepository`/`@Document` — that
  abstraction doesn't cleanly expose `version_type=external`, which this checkpoint's whole
  correctness guarantee depends on.
- `../docker-compose.yml`: added an `elasticsearch` service (single-node, security disabled,
  port 9200) — same "add the real infra once the phase needs it" precedent as Postgres/
  Redis/RabbitMQ in earlier phases.
- `application.yaml`: `spring.elasticsearch.uris`; `spring.rabbitmq.listener.simple.retry`
  (`enabled: true`, `max-attempts: 3`) — Spring's built-in retry-then-reject-without-requeue,
  no custom Java retry config needed, since `catalog.product.changed`'s dead-letter routing
  was already declared in 3a.
- `application-test.yaml`: `management.health.elasticsearch.enabled: false` (same reasoning
  as the existing redis/rabbit disables — confirmed the new `ElasticsearchClient` autoconfig
  bean doesn't break `@SpringBootTest` context startup with no real ES running: connection is
  lazy, only attempted on first real call, which no existing IT makes).

**Sync path:** `domain/port/SearchIndex.upsertProduct(ProductSyncPayload)` — no separate
version parameter; `payload.updatedAtEpochMillis()` (already on the payload since 3a) *is*
the external version. `infra/search/ElasticsearchSearchIndexAdapter` (new `infra/search`
package — 3c adds more here) writes through the `products` alias only, never a versioned
index name directly (Section 3) — index/alias creation stays 3c's job (blue-green reindex).
A 409 version-conflict response from ES is caught and treated as an expected, ignorable
outcome (a stale write losing to newer data already indexed); any other ES/IO failure is
rethrown so the listener's retry/DLQ path actually engages.

`infra/consumer/CatalogProductChangedListener` reads the raw AMQP `Message` body directly —
bypasses the generic `Jackson2JsonMessageConverter` on purpose, matching how
`RabbitCatalogEventPublisher` published raw bytes in 3a, so there's no converter
type-inference to get subtly wrong. A JSON parse failure or an upsert failure is left to
**propagate**, not caught — that's the only thing that lets Spring's configured retry
(3 attempts) then reject-without-requeue actually hand the message to the DLQ instead of it
silently vanishing.

**PROCESSED loop (PLAN_PHASE1.md Section 3 step 5 — included per your call, since
`OutboxStatus.PROCESSED` existed for exactly this since 3a and deferring it would leave
outbox rows permanently stuck at `PUBLISHED` with no path forward, undermining the nightly
reconciliation job 3c is supposed to build):**
- `RabbitCatalogEventPublisher` now stamps the outbox event id as a message header
  (`x-outbox-event-id`, `CatalogQueueConfig`) — needed to correlate a later confirmation
  back to the row that produced it.
- New `catalog.product.indexed` queue (`CatalogQueueConfig`) — deliberately **no DLQ of its
  own**; a lost confirmation just leaves a row at `PUBLISHED`, which is exactly what 3c's
  reconciliation sweep exists to catch, not a silent loss.
- `CatalogProductChangedListener` publishes the confirmation (echoing the same header) right
  after a successful upsert.
- New `infra/consumer/CatalogIndexedConfirmationListener` consumes it and calls the new
  `CatalogOutboxEventRepository.markProcessed(UUID)` — bulk `@Modifying` update, same
  dirty-checking discipline as `markPublished`.

**Tests:**
- `support/RecordingSearchIndex` — test-only `SearchIndex` that **enforces** external
  versioning itself (keeps the higher `updatedAtEpochMillis` per product, silently drops a
  stale write), not a rubber-stamp fake — without that, the ordering test would pass
  regardless of whether production code got versioning right.
- `CatalogProductChangedListenerTest.onMessage_outOfOrderDelivery_higherVersionWins` — your
  required test: publish v2 then v1 out of order, assert the final state still shows v2.
- `CatalogProductChangedListenerTest.onMessage_malformedPayload_throwsRatherThanSwallowing`
  — guards the invariant the DLQ path depends on.
- `CatalogOutboxCorrelationEndToEndTest` (your addition) — wires the **real** publisher and
  **both real** listeners together in-process, mocking only the broker transport
  (`RabbitTemplate`, captured per hop via Mockito) at each of the three hops: publish ->
  consume+upsert+confirm -> consume-confirmation+markProcessed. Asserts the same
  `outboxEventId` created at hop 1 is the exact id `markProcessed` receives at hop 3. Each
  piece already had its own isolated test; this is the one that would actually catch a
  header-name typo or a dropped header between hops, which isolated tests structurally
  cannot.
- **Explicitly out of scope, per PLAN_PHASE1.md Open Question #6's resolution:** no
  Testcontainers-ES, no automated test against a real Elasticsearch. The adapter gets
  manually verified via Swagger once running — same treatment as the OTP SMS stub.
- **Known, deliberate gap:** the ES `products` alias/index doesn't exist yet — full manual
  end-to-end verification (a real product write actually becoming searchable) only becomes
  possible once 3c's reindex job bootstraps it. `ElasticsearchSearchIndexAdapter` is correct
  code with nothing to write to yet; not a 3b bug, a 3c dependency.

### Checkpoint 3b file list (as built)

- `domain/port`: `SearchIndex`; `CatalogOutboxEventRepository` (touched — `markProcessed`).
- `infra/search`: `ElasticsearchSearchIndexAdapter`.
- `infra/consumer`: `CatalogProductChangedListener`, `CatalogIndexedConfirmationListener`.
- `infra/messaging`: `RabbitCatalogEventPublisher` (touched — header stamping).
- `infra/config`: `CatalogQueueConfig` (touched — indexed queue + header constant).
- `infra/persistence`: `CatalogOutboxEventJpaRepository`/`CatalogOutboxEventRepositoryAdapter`
  (touched — `markProcessed`).
- `../build.gradle`, `../docker-compose.yml`, `application.yaml`, `application-test.yaml` (touched).

### Next

Checkpoint 3c — search API + reindex: `/search`, `/search/suggest`, `/search/trending`,
`/search/image` (stub), `SearchQueryBuilder`, blue-green reindex job (also bootstraps the
`products` alias/index this checkpoint's adapter writes to). Not started.

---

## Status: Phase 1, Wave 3, Checkpoint 3a (outbox) — DONE, 87/87 tests green

Outbox only — no ES consumer yet (that's 3b). Proves the write-path guarantee: a Product
write and its sync event land in the same Postgres commit, and the relay recovers from
both a flaky publish and a genuine crash-before-first-relay-run.

**New table** (`db/migration/postgresql/V5__catalog_outbox_events.sql`):
`catalog_outbox_events` (`product_id` FK, `event_type`, `payload` TEXT, `status`
`PENDING`/`PUBLISHED`/`PROCESSED` — `PROCESSED` unused until 3b, same
schema-now-behavior-later precedent as `Review.status`).

**Write path:** new `CatalogWriteService`/`CatalogWriteServiceImpl.saveProductAndEnqueueSync`
— `@Transactional`, saves the `Product` then builds the ES-ready projection
(`ProductSyncPayload`, via the new pure `domain/service/ProductSyncProjectionBuilder`) and
saves the outbox row, one commit. `CatalogSeeder` (the only current write path — no
product-creation endpoint exists) now goes through this instead of calling
`productRepository.save()` directly, so any future admin/vendor write endpoint reuses the
same choke-point rather than re-deriving the outbox-write logic.

**Relay:** `CatalogOutboxRelay` (`application/impl`, `@Scheduled`, no separate interface —
same judgment as `OtpSendService`) polls `PENDING` rows and publishes each via the new
`domain/port/CatalogEventPublisher` (`RabbitCatalogEventPublisher` adapter — synchronous
publisher-confirm wait via `rabbitTemplate.invoke(... waitForConfirms(timeoutMs))`, returns
`false` on nack/timeout rather than throwing — a nack is an expected, branchable outcome).
Per-row try/catch so one failure never blocks the batch. `markPublished` is a bulk
`@Modifying` update (not fetch-then-mutate) — same dirty-checking discipline as
`DeviceRepository.revokeAllActiveByUserId`.

**Queue:** `catalog.product.changed` + its own `catalog.product.changed.dlq` declared from
day one (`infra/config/CatalogQueueConfig`), unlike Phase 0's OTP queue which explicitly
deferred a DLQ. `spring.rabbitmq.publisher-confirm-type: correlated` enabled.
`CatalogOutboxEvent.EVENT_TYPE_PRODUCT_DELETED` constant pinned now
(`"catalog.product.deleted"`, PLAN_PHASE1.md Section 3's literal candidate string) even
though nothing emits it yet — so 3b doesn't pick a delete-event name ad-hoc.

**Two recovery guarantees verified separately, not conflated into one test**
(`CatalogOutboxRelayJpaIT`): `relay_nackForOneRow_...` proves recovery from a flaky publish
(one row nacks, stays `PENDING`, a later poll with the fault gone recovers it) —
`relay_eventSeededDirectlyWithNoPriorRelayRun_...` proves recovery from the actual
crash-window the outbox pattern exists to close (a row no relay run has ever touched,
because the process died between the domain commit and the first relay poll, is still
picked up). Different guarantees, would silently stop testing the second one if merged
into the first.

**Found and fixed mid-checkpoint, not anticipated in the proposal:** `@EnableScheduling`
on the main application class made `CatalogOutboxRelay`'s real `@Scheduled` poller fire
during every `@SpringBootTest` IT — no RabbitMQ broker exists in the test environment, so
every IT's context lifetime spammed connection-refused warnings every 5s (caught, didn't
fail anything, but noisy and wasteful). Fixed by moving `@EnableScheduling` off the main
class onto a new `infra/config/SchedulingConfig`, gated `@Profile("!test")` — same intent
as `application-test.yaml` already disabling the RabbitMQ listener autostart.

**Doc-drift fix:** `CatalogSeeder` actually lives in `infra/seed/`, not `infra/config/` as
the architecture snapshot below claimed — another artifact of the external persistence
restructure (see the recovery note further down), harmless on its own since nothing broke,
but the snapshot was wrong. Noted here; the snapshot itself isn't rewritten line-by-line
for one class's location.

### Checkpoint 3a file list (as built)

- `domain/model`: `CatalogOutboxEvent`, `ProductSyncPayload`.
- `domain/enums`: `OutboxStatus`.
- `domain/service`: `ProductSyncProjectionBuilder`.
- `domain/port`: `CatalogOutboxEventRepository`, `CatalogEventPublisher`.
- `infra/persistence`: `CatalogOutboxEventEntity` + package-private
  `CatalogOutboxEventJpaRepository` + `CatalogOutboxEventMapper` + `CatalogOutboxEventRepositoryAdapter`.
- `infra/messaging`: `RabbitCatalogEventPublisher`.
- `infra/config`: `CatalogQueueConfig` (queue + DLQ + DLX), `SchedulingConfig`.
- `application/service` + `impl`: `CatalogWriteService`/`CatalogWriteServiceImpl`,
  `CatalogOutboxRelay`.
- Touched: `CatalogSeeder` (now calls `CatalogWriteService`), `BuildDashBackendApplication`
  (dropped `@EnableScheduling`, moved to `SchedulingConfig`), `application.yaml`
  (`publisher-confirm-type: correlated`).

### Next

Checkpoint 3b — ES sync: search module's listener, `SearchIndex` port + adapter, external
versioning, DLQ consumer wiring. Not started.

---

## Status: Phase 1, Wave 2 (review/qna/wishlist/backinstock) — DONE, 80/80 tests green

**Persistence-layout recovery (2026-08-18, before Wave 3 started):** something outside
this session's own edits restructured all of `infra/persistence` from the flat
per-feature layout into kind-based `entity/`/`mapper/`/`repository/`/`adapter/`
subpackages, without updating the package-private visibility every adapter relied on to
see its own `*JpaRepository`/`*Mapper` — left the project **not compiling** (44 errors).
Reverted to the flat per-feature layout documented below (package declarations fixed,
subpackage dirs removed, redundant same-package imports stripped) — zero doc drift,
matches what this file already described. 80/80 tests green again after the revert.
Separately found (and left alone, not this session's call): `main`'s git history had
been rewritten into granular commits and pushed to `origin/main`, and a large
uncommitted deletion (`../.env`, `../.idea`, `../.gradle`, `../.remember`, `../build`) was sitting
staged in the index — flagged to the user, not touched.

Built directly in the hexagonal shape (no package-by-feature detour) per the confirmed
mapping. Four sub-domains, each following the same `domain/port` → `infra/persistence`
(`*Entity` + package-private `*JpaRepository` + `*Mapper` + `*RepositoryAdapter`) →
`application/service`+`application/impl` → `api/{controller,dto,mapper}` shape as every
prior slice.

**New tables** (`db/migration/postgresql/V4__review_qna_wishlist_backinstock.sql`):
`reviews`, `questions`, `answers`, `wishlist_entries`, `notify_me_subscriptions`, all FK'd
to `products`/`users`/`questions`.

**Domain decisions made explicit before/during this wave, not left as implicit gaps:**

1. **Verified-purchase check — stubbed, not built.** No Order/OrderItem module exists
   anywhere in the codebase yet (checkout is a later phase), so the feature doc's "join
   against completed orders for SKU+user" has nothing to join against. `Review` gets a
   `verifiedPurchase` boolean column now (default `false`), same principle as the
   `status` moderation field: add the schema now, avoid a migration once Orders exists
   and a real check can be wired in. `ReviewServiceImpl.submit()` accepts every
   submission unconditionally today; `verifiedPurchase` is never set `true` by anything
   and is deliberately **not** exposed on `ReviewResponse` (a badge that's always false
   is misleading, not just incomplete). Revisit once an Orders module lands.
2. **`answer.source` — resolved server-side from JWT roles, not client-supplied.** New
   `domain/service/AnswerSourceResolver` (pure logic, no infra dependency, same shape as
   `OtpGenerator`) checks `AuthenticatedUser.roles()` for `VENDOR`/`STAFF`, defaults to
   `CUSTOMER`. Every token issued today hardcodes `roles=["USER"]` — no vendor/staff
   login flow exists yet — so this resolves `CUSTOMER` for every real caller right now.
   That's expected, not a bug: the resolver is correct-by-construction and needs zero
   changes once a vendor/staff role is actually issued later. Deliberately rejected
   letting the client declare its own source in the request body (trusts a
   self-declared identity with no server-side check).
3. **Question moderation — explicitly NOT moderated, unlike Review/Answer.** The
   feature doc's Open Question #5 only names `Review` and `Answer` for the
   `status` (`pending|approved|rejected`) field; `Question` was never included. This
   wave keeps that scope as-is on purpose: `questions` has no `status` column, no
   `ModerationStatus` field, and `POST /products/{id}/questions` goes live immediately.
   Rationale — a customer's product question carries far lower abuse/liability risk
   than a public review or an answer purporting to speak for a vendor, so gating it
   the same way would be scope creep beyond what was asked. If moderation is later
   wanted for questions too, it's an additive column + a new `ModerationStatus` field
   on `Question`, not a redesign — `ModerationStatus` already exists and is reused
   as-is (same reuse precedent as `ProductStatus` across catalog entities).
4. **`WishlistEntryResponse` carries only `productId`/`createdAt`, no product
   enrichment** (name/image/price) — Pricing doesn't exist until Phase 2, and product
   detail is already a separate call (`GET /products/{id}`). Same precedent as
   catalog's listing endpoint omitting price for the same reason.
5. **Wishlist-add and notify-me-subscribe are both idempotent**, not error-on-duplicate:
   re-adding an already-wishlisted product or re-subscribing to an already-subscribed
   product returns the existing row instead of a 409/400. Simpler contract, no
   arbitrary asymmetry between the two nearly-identical "ensure this relationship
   exists" operations.
6. **Back-in-stock notification job is out of scope for this wave, by design, not an
   oversight.** `NotifyMeSubscriptionService` only records the subscription — the "an
   inventory-update event triggers a job that checks subscribers and fires
   notifications" half of the feature doc has nothing to trigger from, since
   `Product.stock` is still the static Phase-1 stub from PLAN_PHASE1.md Open Question
   #1 (no real warehouse/vendor event source exists). Revisit together once that stub
   is replaced with a real event.

**Dirty-checking write-path audit (explicitly checked, not assumed):** every write path
in this wave — `ReviewServiceImpl.submit`, `QnaServiceImpl.ask`/`answer`,
`WishlistServiceImpl.add`, `NotifyMeSubscriptionService.subscribe` — only ever
constructs a **new** domain object and calls `repository.save()` explicitly. None of
them fetch an existing entity and mutate it in place, so the Slice-4/5-style
dirty-checking gap (relying on JPA auto-flush inside `@Transactional`) cannot occur
here — there's nothing fetched-then-mutated to begin with. No fix needed, confirmed by
inspection of all four write paths.

**N+1 guard for `QnaServiceImpl.listThreads` (explicitly verified per your instruction,
not just written and trusted):** `AnswerRepository.findByQuestionIdIn` batch-fetches
every answer for a product's questions in one query — never called per-question in a
loop. Verified with a real query-count assertion, not just a code read:
`QnaServiceJpaIT.listThreads_withMultipleQuestionsAndAnswers_batchFetchesAnswersInOneQuery`
enables Hibernate statistics (`hibernate.generate_statistics: true`, test profile only,
`application-test.yaml`), seeds 5 questions each with 1 answer, clears the statistics,
calls `listThreads`, and asserts `Statistics.getPrepareStatementCount() == 2` — one
`SELECT` for the questions, one batched `SELECT ... WHERE question_id IN (...)` for
their answers, a count that stays flat regardless of how many questions exist. If a
future change reverts to a per-question fetch, this test fails on the statement count
before anyone notices it in production query logs.

**Found and fixed during verification, not anticipated in the file-list proposal:**
`reviews.rating` was declared `SMALLINT` in the migration, but Hibernate maps Java `int`
to `INTEGER` by default — `ddl-auto: validate` failed at context startup with
`wrong column type encountered in column [rating] ... found [int2], but expecting
[integer]`. Fixed by changing the column to `INTEGER` in the migration (matches the
entity's plain `int` field) rather than adding a `@Column(columnDefinition=...)`
override — the simpler fix, and consistent with how every other numeric column in this
codebase is left to Hibernate's default mapping.

**Test infrastructure note:** Docker Desktop's daemon was not running at the start of
this session (`docker version` failed to reach the socket) — every `@SpringBootTest` IT
failed with a Testcontainers connection error before any code-level issue could even
surface. Started via `systemctl --user start docker-desktop`; not a code change, purely
an environment precondition already documented in the Slice 6 test-infrastructure
trade-off below.

**80/80 tests green** (`./gradlew test`, clean run) — up from Slice 6's 60/60. New:
5 `AnswerSourceResolverTest` cases, 5 infra mapper round-trip tests (`Review`/`Question`/
`Answer`/`WishlistEntry`/`NotifyMeSubscription`), 1 `QnaServiceJpaIT` (the N+1 guard),
9 controller ITs across `ReviewControllerIT`/`QnaControllerIT`/`WishlistControllerIT`/
`NotifyMeControllerIT`.

### Wave 2 file list (as built)

- `domain/model`: `Review`, `Question`, `Answer`, `WishlistEntry`,
  `NotifyMeSubscription`, `QuestionThread` (enriched read-model, question + its answers).
- `domain/enums`: `ModerationStatus` (shared by `Review`/`Answer`), `AnswerSource`.
- `domain/service`: `AnswerSourceResolver`.
- `domain/port`: `ReviewRepository`, `QuestionRepository`, `AnswerRepository`,
  `WishlistRepository`, `NotifyMeSubscriptionRepository`.
- `infra/persistence`: `{Review,Question,Answer,WishlistEntry,NotifyMeSubscription}Entity`
  + package-private `*JpaRepository` + `*Mapper` + `*RepositoryAdapter` (20 files).
- `application/service`: `ReviewReader`/`ReviewWriter`, `QnaReader`/`QnaWriter`,
  `WishlistReader`/`WishlistWriter` (ISP split, same precedent as
  `UserProfileReader`/`Writer`).
- `application/impl`: `ReviewServiceImpl`, `QnaServiceImpl`, `WishlistServiceImpl`,
  `NotifyMeSubscriptionService` (no separate interface — single caller, single workflow,
  same judgment call as `OtpSendService`).
- `api/dto/request`: `SubmitReviewRequest`, `AskQuestionRequest`, `AnswerQuestionRequest`,
  `AddWishlistItemRequest`.
- `api/dto/response`: `ReviewResponse`, `QuestionResponse` (nested `AnswerResponse[]`),
  `AnswerResponse`, `WishlistEntryResponse`, `NotifyMeSubscriptionResponse`.
- `api/mapper`: `ReviewMapper`, `QnaMapper`, `WishlistMapper`, `NotifyMeMapper`.
- `api/controller`: `ReviewController` (`GET/POST /products/{id}/reviews`),
  `QnaController` (`GET/POST /products/{id}/questions`, `POST /questions/{id}/answers`),
  `WishlistController` (`GET/POST /users/me/wishlist`,
  `DELETE /users/me/wishlist/{productId}`), `NotifyMeController`
  (`POST /products/{id}/notify-me`).
- `SecurityConfig` — **unchanged.** Its existing matchers (`GET /products/**`
  permitAll, `/users/**` hasRole(USER), else `authenticated()`) already covered every
  Wave 2 route correctly; verified before writing any controller, not assumed after.

### Next

Wave 3 not yet scoped — see PLAN_PHASE1.md for the full Phase 1 plan (search/ES,
recommendations, pricing/tax are later phases).

---

## Status: Phase 1, Wave 1 (Catalog listing/detail) — VERIFIED, awaiting your go-ahead for Wave 2

Per PLAN_PHASE1.md, sequenced in three waves. Wave 1 built: `Category`/`Product` Mongo
documents, `ProductFactory` (schema-driven attribute validation), `CatalogController`
(listing + detail only). 53/53 tests pass (48 prior + 5 new, see "Wave 1 verification" below).

### What's built (Wave 1)

- `catalog.Category` / `catalog.Product` — MongoDB documents. `Category.attributeSchema`
  drives per-category variable attributes (Section 2 of PLAN_PHASE1.md) — no per-category
  Java branches.
- `catalog.ProductFactory` (Factory pattern) / `SchemaDrivenProductFactory` — one generic
  implementation validates any category's attributes against its schema; a category needing
  bespoke construction logic later gets its own impl behind the same interface.
- `catalog.CategoryReader` / `catalog.ProductReader` (narrow, DIP) + `CatalogServiceImpl` —
  same one-impl-many-narrow-interfaces shape as Phase 0's `UserServiceImpl`.
- `CatalogController` — `GET /categories`, `GET /categories/{id}`, `GET /products`
  (cursor-paginated via Mongo `_id`), `GET /products/{id}` (includes HSN/GST tag resolved
  from Phase 0's `hsn_gst_rates` table, and a derived `in_stock`/`out_of_stock` status).
  All public (`SecurityConfig` permits GET on `/categories/**` and `/products/**`).
- `CatalogSeeder` (`@Profile("dev")`) — seeds 2 categories (Cement, Paint) + 3 products on
  startup so Swagger smoke-testing works without a product-creation endpoint (none exists;
  catalog writes are vendor/admin-side, out of scope here).

### Deviations from the original feature-doc endpoint shape

- `/products` does **not** support `price_min`/`price_max`/`sort` yet — `Product` has no
  price field until Phase 2 (Pricing) exists (PLAN_PHASE1.md Section 2 explicitly deferred
  it). Listing currently supports `category`, `brand`, and cursor pagination only, fixed
  insertion-order (Mongo `_id` ascending). Revisit once Pricing lands.
- No product-creation endpoint in Wave 1 (or planned in Phase 1 at all) — see `CatalogSeeder`
  note above.

### Mongo test infrastructure

Added `org.testcontainers:junit-jupiter` + `:mongodb` (test-only). `CatalogServiceMongoIT`
exercises `CatalogServiceImpl` against a real Mongo container instead of mocks —
`CatalogControllerIT` still only covers controller routing/serialization. Annotated
`@Testcontainers(disabledWithoutDocker = true)` so it skips cleanly (not a hard failure) on a
machine with no Docker at all, rather than reusing `AbstractIntegrationTest`'s
no-Docker-required guarantee.

**Docker Desktop socket issue — root-caused and fixed at the project level, no GUI step
needed.** Testcontainers was failing to start any container here with `400 Bad Request` from
Docker's socket. Traced with wire-level logging (`org.apache.hc.client5.http.wire` DEBUG):
Testcontainers' bundled docker-java client hardcodes API version `v1.32` for its initial
`GET /v1.32/info` probe whenever no API version is otherwise configured
(`DockerClientProviderStrategy.getClientForConfig`, falls back to
`RemoteApiVersion.VERSION_1_32` when `getApiVersion() == UNKNOWN_VERSION`). This Docker
Desktop install (Engine 29.6.2) reports `MinAPIVersion: 1.40` and rejects anything older with
`400` — confirmed directly with `curl --unix-socket .../docker.sock http://localhost/v1.32/info`
returning the same empty-stub `400` response docker-java saw, while `/v1.40/info` returns real
data. Not Desktop-for-Linux-specific and not a socket-path problem — any Docker Engine that
raises `MinAPIVersion` above 1.32 hits this with old docker-java defaults. `DOCKER_API_VERSION`
(env var) has no effect; the version docker-java actually reads is the **JVM system property**
`api.version` (confirmed via bytecode inspection of the shaded `DefaultDockerClientConfig`).
Fix, in `../build.gradle`'s `test` task: `systemProperty 'api.version', '1.43'`. Verified with
`~/.testcontainers.properties` deleted entirely and on the Spring-Boot-managed testcontainers
version (no version bump needed) — the fix is the one line, nothing else. Applies to Wave 3's
Testcontainers-Elasticsearch too, since it's the same client hitting the same daemon.

### Wave 1 verification

Requested checks — `CatalogServiceMongoIT` now actually runs (see fix above), plus the earlier
live-dev-stack probes that verified the same behavior before the fix landed:

1. **Cursor pagination fragility** — inline comment added at `CatalogServiceImpl.java`
   (`list()`, above the cursor `Criteria`) documenting the single-node/insertion-order
   assumption. `CatalogServiceMongoIT.cursorPagination_walksTwoPagesWithoutSkipOrDuplicate`
   runs for real against a Testcontainers Mongo and passes. Also verified directly against the
   live dev stack: seeded 8 extra products, walked `/products?category=...&limit=4` across 3
   pages — 10 items returned, 10 unique, zero duplicates, zero gaps. **PASS.**
2. **HSN/GST tag correctness** — `CatalogServiceMongoIT.getDetail_gstRateMatchesHsnGstRatesTableExactly`
   runs for real and passes. Also verified live via `GET /products/{id}`: HSN `2523` (Cement) →
   `28.00`, HSN `3208` (Paint) → `18.00` — both match `V2__seed_hsn_gst_rates.sql`/H2 mirror
   exactly. **PASS.**
3. **stockStatus derivation** — 3 test cases in `CatalogServiceMongoIT`, including the
   quantity=0-across-all-warehouses boundary, run for real and pass. Also verified live by
   seeding 3 products directly: `stock:[{0},{0}]` → `out_of_stock`, `stock:[{0},{7}]` →
   `in_stock`, `stock:[]` (no entries) → `out_of_stock`. **PASS.**
4. **No Elasticsearch in the catalog read path** — `grep -rniE "elasticsearch|elastic"` across
   `../src/main/java`, `../src/main/resources`, `../build.gradle` → zero matches. Traced
   `CatalogController.getProduct` → `CatalogServiceImpl.getDetail`: touches only
   `ProductRepository`/`CategoryRepository` (Mongo) and `HsnGstRateRepository` (Postgres/JPA).
   No ES client, no ES dependency in `../build.gradle`. **Confirmed true** — Wave 3's
   not-yet-built ES/search module cannot be accidentally short-circuited to.

Test data seeded for checks 1 and 3 was inserted directly into and deleted from the live dev
Mongo container after verification — the 3 `CatalogSeeder` products are the only catalog data
left in that database.

### Next

**Wave 2 (`review/`, `qna/`, `wishlist/`, `backinstock/`) does NOT start automatically** —
waiting on your explicit go-ahead per this round's instructions, even though all 4 checks
above passed.

---

## Phase 0 (Account & Identity + HSN/GST seed) — COMPLETE

All endpoints implemented, tested, and verified end-to-end against both real infra
(docker-compose Postgres/Redis/Mongo/RabbitMQ) and a fully standalone test suite (H2 +
jedis-mock, no Docker required). See `SWAGGER_TEST_CASES.md` for manual verification
scenarios and `INFRA.md` for the phase-to-infrastructure mapping.

## What's built

- **Auth**: OTP send/verify (Redis-backed, rate-limited, 3-attempt lockout), Google sign-in,
  guest sessions, JWT access/refresh/guest tokens, refresh rotation with reuse detection
- **Bloom filter**: `PhoneExistenceIndex` (Guava-backed) pre-checks phone existence on
  `POST /auth/otp/send`, surfaced as `existingUser` in the response; repopulated from Postgres
  at startup so a restart doesn't misreport existing users as new
- **RabbitMQ**: `POST /auth/otp/send` publishes to the `otp.dispatch` queue instead of calling
  the SMS provider inline; a listener consumes and delegates to the existing `OtpSender`
- **User**: profile CRUD (name, business name, GSTIN format validation)
- **Device**: session registry (list/revoke/logout-all-devices)
- **Login history**: append-only audit log
- **Tax**: HSN/GST master table, seeded with 12 real Indian codes across cement/steel/bricks/
  pipes/paint/electrical (not yet exposed via endpoint — nothing consumes it until Catalog phase)
- **Health**: Spring Actuator

## Architecture (SOLID, package-by-feature)

Every feature that previously had one "god service" is now a narrow interface (or a small set
of them) backed by one implementation class. Controllers depend only on interfaces (DIP);
Spring wires the concrete `*Impl`/`*Service` classes in automatically.

| Package | Interfaces (caller-facing) | Impl | One-line SOLID check |
|---|---|---|---|
| `auth.otp` | `OtpStore`, `OtpRateLimiter`, `OtpSender`, `OtpDispatchQueue` | `RedisOtpStore`, `RedisOtpRateLimiter`, `SmsOtpSender`, `RabbitOtpDispatchQueue` | Each interface has exactly one reason to change (storage / policy / channel / transport) — growing any one wouldn't touch the others |
| `auth.otp` | — (orchestrators) | `OtpSendService`, `OtpVerificationService` | If these grew, it'd be because the send/verify *workflow* changed, not because of Redis details, SMS provider, or queue transport swaps — SRP holds |
| `user` | `PhoneExistenceIndex` | `BloomFilterPhoneExistenceIndex` | Single job (might/mark) — swapping the backing structure (e.g. a Redis-backed bloom filter for multi-instance deployments) never touches `AuthServiceImpl` |
| `auth.jwt` | `TokenIssuer`, `TokenValidator` | `JwtTokenIssuer`, `JwtTokenValidator` (share package-private `JwtCodec`) | Filter only sees `TokenValidator`, AuthService only sees `TokenIssuer` — growing one never forces the other to change (ISP) |
| `device` | `DeviceRegistry`, `RefreshTokenRotator` | `DeviceServiceImpl` | Session bookkeeping vs refresh-token crypto are genuinely different callers/reasons to change — split even though one class backs both |
| `user` | `UserAccountService`, `UserProfileReader`, `UserProfileWriter` | `UserServiceImpl` | GET-only caller (UserController read) never depends on write capability, and vice versa |
| `loginhistory` | `LoginEventRecorder`, `LoginHistoryReader` | `LoginEventServiceImpl` | AuthService only writes, the history endpoint only reads — no caller sees the other half |
| `auth` | `AuthenticationFacade` | `AuthServiceImpl` | Orchestration only — every actual decision (OTP policy, token crypto, device state) is delegated, so this class's only reason to change is the login/session *workflow* itself |

**OCP in practice**: a new OTP delivery channel (email, WhatsApp) = a new `OtpSender`
implementation, zero edits to `OtpSendService`. A new rate-limit policy (sliding window,
per-IP) = a new `OtpRateLimiter` implementation, zero edits to either OTP orchestrator. A new
async transport (SQS, Kafka) = a new `OtpDispatchQueue` implementation — `OtpSender` and
`OtpDispatchQueue` are deliberately separate interfaces: one is "how do we get this message
somewhere reliably" (transport), the other is "how do we actually deliver it to the user"
(channel) — conflating them would mean a transport swap forces a channel-abstraction change too.

**LSP note**: `OtpStore.check()` returns a 3-value `OtpMatchResult` enum (`NOT_FOUND` /
`MISMATCH` / `MATCH`) instead of a boolean, specifically so "no OTP on file" is never
conflated with "wrong code" — a substitutable implementation can't quietly narrow that
contract into a 2-state boolean.

## Test infrastructure

No Docker required. `AbstractIntegrationTest` wires:
- **H2** in-memory (`MODE=PostgreSQL`), migrated via `db/migration/h2/*.sql` — a hand-maintained
  H2-compatible mirror of the real `db/migration/postgresql/*.sql` migrations. Flyway resolves
  the right folder automatically via `spring.flyway.locations: classpath:db/migration/{vendor}`.
- **jedis-mock** — a pure-Java in-memory Redis (RESP protocol) server started once per JVM, so
  the real `StringRedisTemplate`/Lettuce client connects unchanged.
- **RabbitMQ stays fully dormant in tests** — no fake broker needed. `RecordingOtpDispatchQueue`
  (`@Primary` in test scope) delivers synchronously in-process instead of publishing, and
  `spring.rabbitmq.listener.simple.auto-startup: false` + `management.health.rabbit.enabled: false`
  stop the listener container and health indicator from ever trying to connect.

37/37 tests pass standalone (`./gradlew test`, no external services needed):
- 6 `JwtTokenIssuerValidatorTest` + 6 OTP unit tests (Mockito, no Spring context)
- 25 MockMvc integration tests across Auth/User/Device/LoginHistory controllers + 1 smoke test

**Known test-infra tradeoff**: the H2 and Postgres migration files are maintained by hand as
two copies — H2 can't run the exact same DDL (no `pgcrypto`/`gen_random_uuid()`, different
timestamp/UUID default syntax). If the real schema changes, both folders need updating.

## Deviations / fixes made during implementation

- `JwtCodec` stamps a random `jti` claim on every token — without it, two tokens issued for the
  same subject+device within the same second come out byte-identical (JWT `iat`/`exp` are
  second-precision), which silently broke refresh-rotation semantics.
- `RefreshTokenRotator.validateForRefresh` uses `@Transactional(noRollbackFor = UnauthorizedException.class)`
  — the reuse-detection revoke is a side effect of the same exception that reports the failure,
  and Spring's default rollback-on-RuntimeException would otherwise undo the revoke.
- `RedisOtpRateLimiter.enforceSendAllowed` skips the cooldown `SET` entirely when
  `otp.send-cooldown-seconds <= 0` — Redis's `SETEX` rejects a non-positive TTL outright, so a
  future `OTP_SEND_COOLDOWN_SECONDS=0` override (intended to mean "disabled") would otherwise
  crash the endpoint.
- `GlobalExceptionHandler`'s catch-all now logs unhandled exceptions — it was silently
  swallowing 500s before, which is how the above Redis bug took real debugging effort to find.
- **Bloom filter + RabbitMQ added this session.** A later prompt asserted both were "already
  scoped in the Phase 0 prompt" — grepping the actual codebase, `PROGRESS.md`, and `../build.gradle`
  found zero trace of either; they were never built or mentioned in any prior Phase 0 session.
  Built both from scratch now (`PhoneExistenceIndex`/`BloomFilterPhoneExistenceIndex`,
  `OtpDispatchQueue`/`RabbitOtpDispatchQueue`/`OtpDispatchListener`), verified against real
  Postgres + RabbitMQ (not just the standalone test suite), and documented the phase-to-infra
  mapping in `INFRA.md` as requested.

## Not in Phase 0 (explicitly descoped, see prior session's plan)

Phone-change, email add/verify, password reset, TOTP/2FA, privacy consent, account deletion.
HSN/GST master table has no REST endpoint yet — first consumer is the Catalog phase.

## Next phase

Catalog (Phase 1) is now in progress — see the "Phase 1, Wave 1" section at the top of this
file and `PLAN_PHASE1.md` for the full plan.

## Hexagonal/layered architecture migration (in progress)

Migrating from package-by-feature to layered architecture: `api/{controller,dto/request,
dto/response,mapper}`, `application/{impl,service,validator}`, `common/`, `domain/{enums,
exception,model,port,service}`, `infra/{cache,config,consumer,external,messaging,
persistence,security}`. Package root stays `com.builddash.backend`. Template folders with
no current equivalent (`ledger/`, `genfin/`, `document/`, `policy/`, `notification/`)
omitted — add only when a feature needs them. Full DTO purity (use-case interfaces return
domain/model, `api/mapper` builds response DTOs) and strict exception layering (domain
exceptions drop `HttpStatus`, `GlobalExceptionHandler` maps status) are both in scope,
confirmed by user 2026-08-17.

Sequence: tax → auth/jwt → auth/otp → user (+auth/google) → auth core+device+loginhistory →
catalog (folded with a Mongo→Postgres/JPA migration, since Mongo removal was never executed
and shouldn't touch Product/Category twice). Full test suite must stay green after each slice.

**Slice 1 — tax: DONE (2026-08-17).**
- `tax/entity/HsnGstRate.java` (JPA `@Entity`) split into `domain/model/HsnGstRate.java`
  (framework-free POJO) + `infra/persistence/HsnGstRateEntity.java` (the real `@Entity`,
  keeps `@PrePersist`/`@PreUpdate`).
- `tax/repository/HsnGstRateRepository.java` (`JpaRepository`) → package-private
  `infra/persistence/HsnGstRateJpaRepository.java`.
- New: `domain/port/HsnGstRateRepository.java` (port, method `findByHsnCode` — renamed
  from `findById` for domain vocabulary), `infra/persistence/HsnGstRateRepositoryAdapter.java`
  (implements the port), `infra/persistence/HsnGstRateMapper.java` (entity→domain only, no
  `toEntity` — HSN rates are Flyway-seeded read-only master data, no Java write path exists).
- `catalog/service/impl/CatalogServiceImpl.java` updated to depend on the domain port +
  domain model instead of the JPA repo + entity directly.
- Old `tax/` package deleted (now empty).
- Fixed as a prerequisite (not part of this migration): several stale imports left over
  from a prior session's incomplete package-by-feature restructure (`auth.dto.*` →
  `auth.dto.request/response.*`, `auth.AuthenticationFacade` → `auth.service.
  AuthenticationFacade`) were blocking compilation entirely — corrected in
  `AuthServiceImpl`, `AuthenticationFacade`, `DeviceController`.
- 53/53 tests green (including `CatalogServiceMongoIT`, which exercises the new port via
  Testcontainers).

**Slice 2 — auth/jwt: DONE (2026-08-17).**
- `auth/jwt/IssuedToken.java` → `domain/model/IssuedToken.java`; `auth/jwt/TokenType.java` →
  `domain/enums/TokenType.java`.
- `auth/jwt/service/TokenIssuer.java`, `TokenValidator.java` → `domain/port/`.
  `TokenValidator`'s JJWT `Claims` leak (flagged after this slice) is now resolved: new
  `domain/model/TokenClaims.java` (`userId`, `deviceId`, `roles` — `deviceId` is `null`
  when the token carries no device claim, e.g. guest tokens) replaces `Claims` in the
  interface signature. `TokenValidator.validate()` now returns `TokenClaims`; the
  `subject`/`deviceId`/`roles` extraction methods are gone from the interface entirely —
  `infra/security/JwtTokenValidator` does the JJWT-`Claims`-to-`TokenClaims` mapping
  internally (including the null-safe deviceId parse), so `Claims` never leaves
  `infra/security`. Callers (`AuthServiceImpl`, `JwtAuthenticationFilter`) simplified
  accordingly — `JwtAuthenticationFilter` no longer needs its `type == ACCESS ? ... : null`
  branch to avoid a parse crash on guest tokens, since `TokenClaims.deviceId()` is safely
  null already.
- `auth/jwt/service/impl/{JwtCodec,JwtTokenIssuer,JwtTokenValidator}.java` →
  `infra/security/` (JJWT signing/parsing is an infra concern).
- `auth/jwt/config/JwtProperties.java` → `infra/config/JwtProperties.java`.
- `auth/jwt/aspect/JwtAuthenticationFilter.java` → `infra/security/JwtAuthenticationFilter.java`.
- `JwtTokenIssuerValidatorTest` moved to `infra/security` (same package as package-private
  `JwtCodec`, which its `@BeforeEach` constructs directly).
- Updated: `AuthServiceImpl`, `SecurityConfig` (only two external consumers).
- Old `auth/jwt/` package deleted (main + test).
- 53/53 tests green.

**Slice 3 — auth/otp: DONE (2026-08-17).**
- `auth/otp/OtpMatchResult.java` → `domain/enums/OtpMatchResult.java`.
- `auth/otp/service/{OtpSender,OtpStore,OtpRateLimiter}.java`,
  `auth/otp/publisher/OtpDispatchQueue.java` → `domain/port/` (unchanged otherwise — no
  infra library types in these signatures, no leak to fix).
- `auth/otp/service/impl/OtpGenerator.java` → `domain/service/OtpGenerator.java` (pure
  logic, zero infra dependency).
- `auth/otp/service/impl/{OtpSendService,OtpVerificationService}.java` →
  `application/impl/` (no separate use-case interface existed for either — moved as
  concrete orchestration classes, matching the confirmed mapping).
- `auth/otp/service/impl/{RedisOtpStore,RedisOtpRateLimiter}.java` → `infra/cache/`.
- `auth/otp/service/impl/SmsOtpSender.java` → `infra/external/`.
- `auth/otp/publisher/RabbitOtpDispatchQueue.java` → `infra/messaging/`; `auth/otp/event/
  OtpDispatchMessage.java` → `infra/messaging/` (wire payload, infra concern).
- `auth/otp/listener/OtpDispatchListener.java` → `infra/consumer/`.
- `auth/otp/config/{OtpProperties,OtpQueueConfig}.java` → `infra/config/`.
- **Known, accepted exception (not a leak):** `application/impl/OtpSendService` and
  `AuthServiceImpl` inject `infra/config/OtpProperties` directly — application depending
  on an infra-packaged class. Config-properties beans are treated differently from
  behavior-bearing gateways/adapters (no swappable implementation, nothing to invert);
  forcing a domain port over a settings holder would be exactly the kind of speculative
  abstraction this migration is avoiding. Same precedent as `JwtProperties` in Slice 2.
- Updated: `AuthServiceImpl` (only main-code consumer), test support classes
  `RecordingOtpDispatchQueue`/`RecordingSmsGateway` (now implement `domain/port`
  interfaces), `OtpSendServiceTest`/`OtpVerificationServiceTest` moved to
  `application/impl` to mirror the classes they test.
- Old `auth/otp/` package deleted (main + test).
- 53/53 tests green.

**Slice 4 — user + auth/google: DONE (2026-08-17).**
- `user/entity/User.java` split: `domain/model/User.java` (framework-free POJO) +
  `infra/persistence/UserEntity.java` (the real `@Entity`). `user/entity/GstinStatus.java` →
  `domain/enums/GstinStatus.java` (reused as-is by the entity, no duplicate).
- `user/repository/UserRepository.java` → package-private `infra/persistence/
  UserJpaRepository.java`; new `domain/port/UserRepository.java` (`findById/findByPhone/
  findByEmail/findByGoogleId/findAllPhones/save`), `infra/persistence/
  UserRepositoryAdapter.java`, `infra/persistence/UserMapper.java` (both directions —
  `save` needed domain→entity too, unlike the read-only `HsnGstRateMapper`).
- **Not just a move — a correctness fix required by the split:** `UserServiceImpl.
  updateProfile` and the "backfill googleId onto an existing phone/email match" branch of
  `findOrCreateByGoogle` used to rely on JPA dirty-checking (mutating a still-managed
  entity inside `@Transactional`, auto-flushed at commit). Once `User` is a plain domain
  POJO returned by the port, that auto-flush no longer happens — both methods now call
  `userRepository.save(user)` explicitly after mutating. Without this fix, profile updates
  and Google-account linking would silently stop persisting.
- Full DTO purity applied: `UserProfileReader.getProfile` and `UserProfileWriter.
  updateProfile` now return `domain/model/User` instead of `UserProfileResponse`.
  `UserProfileWriter.updateProfile` takes the three editable fields individually
  (`name, businessName, gstNumber`) rather than the `UpdateProfileRequest` DTO — no
  wrapper "command" type introduced for a single caller, `api/controller/UserController`
  unpacks the request DTO before calling in and uses the new `api/mapper/UserMapper` to
  build `UserProfileResponse` from the returned domain `User`.
- `user/service/{UserAccountService,UserProfileReader,UserProfileWriter}.java` →
  `application/service/`; `user/service/impl/UserServiceImpl.java` → `application/impl/`.
- `user/service/PhoneExistenceIndex.java` → `domain/port/`; `user/service/impl/
  BloomFilterPhoneExistenceIndex.java` → `infra/cache/`; `user/config/
  PhoneExistenceIndexInitializer.java` → `infra/config/` (now depends on the domain port,
  not the JPA repo).
- `user/annotation/{ValidGstin,GstinValidator}.java` → `application/validator/`.
- `user/controller/UserController.java` → `api/controller/`; `user/dto/request/
  UpdateProfileRequest.java`, `user/dto/response/UserProfileResponse.java` → `api/dto/`.
- `auth/google/GoogleTokenVerifier.java` split: new `domain/port/GoogleIdentityGateway.java`
  (extracted — no interface existed before this) + `infra/external/GoogleTokenVerifier.java`
  (implementation, unchanged body). `auth/google/GoogleUserInfo.java` →
  `domain/model/GoogleUserInfo.java`.
- Updated: `AuthServiceImpl` (all the above imports/types, field renamed
  `googleTokenVerifier` → `googleIdentityGateway`), `AuthControllerIT` (`@MockBean` type
  now `GoogleIdentityGateway`), `UserControllerIT` moved to `api/controller` (pure
  black-box HTTP test, no source coupling to move).
- Old `user/` and `auth/google/` packages deleted (main + test).
- 53/53 tests green.

**Slice 5 — auth core + device + loginhistory: DONE (2026-08-17).**
- **Dirty-checking audit (explicitly checked per your instruction, not assumed):**
  - `device` — **had the bug**, same class as User in Slice 4. `DeviceServiceImpl.revoke()`
    and `.validateForRefresh()` (the reuse-detection revoke branch) both mutated a fetched
    `Device` inside `@Transactional` with no explicit `save()`, relying on JPA dirty-checking.
    `.rotateHash()` had the same gap. All three now call `deviceRepository.save(device)`
    explicitly after mutating. `.create()` already called `save()` explicitly — no fix
    needed there. `.revokeAll()` uses a `@Modifying`/`@Query` bulk `UPDATE` (no entity
    round-trip at all) — never relied on dirty-checking, no fix needed.
  - `loginhistory` — **no bug.** `LoginEventServiceImpl.record()` only ever creates a new
    `LoginEvent` and calls `save()` explicitly; `list()` is read-only. Nothing here ever
    mutated a fetched entity in place, so the domain-model split introduces no gap.
  - Confirmed by test: `DeviceControllerIT.revokeDevice_ownDevice_thenRefreshTokenNoLongerWorks`
    and `.refresh_reusingRotatedToken_returnsUnauthorizedAndRevokesDevice` exercise these
    exact paths end-to-end and pass.
- `auth/dto/{request,response}/*.java` (6 files) → `api/dto/{request,response}/`.
- `auth/controller/AuthController.java` → `api/controller/AuthController.java`.
- `auth/service/AuthenticationFacade.java` → `application/service/AuthenticationFacade.java`.
  Full DTO purity applied: new `domain/model/AuthSession.java` (accessToken/refreshToken/
  tokenType/expiresInSeconds) and `domain/model/OtpSendResult.java` (expiresInSeconds/
  existingUser) replace `AuthTokensResponse`/`OtpSendResponse` as return types; every method
  parameter that was an api/dto (`OtpVerifyRequest`, `GoogleSignInRequest`, `RefreshRequest`)
  is now unpacked into plain `String` args — same treatment as `UserProfileWriter` in
  Slice 4, no wrapper command type introduced. New `api/mapper/AuthMapper.java` builds
  `AuthTokensResponse`/`OtpSendResponse` (including the static `"OTP sent"` message text,
  which is presentation copy, not domain data) in the controller.
- `auth/service/impl/AuthServiceImpl.java` → `application/impl/AuthServiceImpl.java`.
- `device/entity/Device.java` → `domain/model/Device.java` + `infra/persistence/
  DeviceEntity.java`. `device/repository/DeviceRepository.java` → package-private
  `infra/persistence/DeviceJpaRepository.java` + new `domain/port/DeviceRepository.java`
  (`save/findById/findByIdAndUserId/findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc/
  revokeAllActiveByUserId`) + `DeviceRepositoryAdapter`/`DeviceMapper` (both directions).
- `device/service/{DeviceRegistry,RefreshTokenRotator}.java` → `application/service/` —
  `DeviceRegistry` returns `domain/model/Device` instead of `DeviceResponse`.
  `device/service/impl/DeviceServiceImpl.java` → `application/impl/`.
- `device/controller/DeviceController.java` → `api/controller/`; `device/dto/response/
  DeviceResponse.java` → `api/dto/response/`; new `api/mapper/DeviceMapper.java`.
- `loginhistory/entity/{LoginEvent,LoginEventType}.java` → `domain/model/LoginEvent.java` +
  `domain/enums/LoginEventType.java` + `infra/persistence/LoginEventEntity.java`.
  `loginhistory/repository/LoginEventRepository.java` → package-private `infra/persistence/
  LoginEventJpaRepository.java` + new `domain/port/LoginEventRepository.java` + adapter +
  mapper (both directions — `record()` needs domain→entity for `save`).
- `loginhistory/service/{LoginEventRecorder,LoginHistoryReader}.java` → `application/
  service/` — `LoginHistoryReader` returns `List<domain.model.LoginEvent>` instead of
  `List<LoginEventResponse>`. `loginhistory/service/impl/LoginEventServiceImpl.java` →
  `application/impl/`.
- `loginhistory/controller/LoginHistoryController.java` → `api/controller/`;
  `loginhistory/dto/response/LoginEventResponse.java` → `api/dto/response/`; new
  `api/mapper/LoginEventMapper.java`.
- Test files moved to `api/controller` to mirror their controllers: `AuthControllerIT`
  (`@MockBean` already `GoogleIdentityGateway` from Slice 4, no further change),
  `DeviceControllerIT`, `LoginHistoryControllerIT` — all pure black-box HTTP tests, no
  source-level coupling to update beyond the package move.
- Old `auth/`, `device/`, `loginhistory/` packages deleted entirely (main + test) — `auth/`
  is now fully retired as a package, its contents distributed across `domain/`,
  `application/`, `infra/`, `api/`.
- 53/53 tests green.

**Slice 6, checkpoint 6a — catalog data layer (Mongo → Postgres/JPA): DONE (2026-08-17).**

Split into 6a (data layer only) / 6b (ports/adapters/application/api + full pagination
rewrite) per your instruction, given the size. 6a scope resolved via a confirmed decision:
old catalog/entity + catalog/repository (Mongo) are deleted now, and the still-untouched
CatalogServiceImpl/SchemaDrivenProductFactory/CatalogSeeder call the new JPA repositories
**directly** (temporarily public, no port/adapter yet) — the alternative (leaving Mongo in
place through 6a) would have left "no Mongo dependency in build.gradle" unsatisfied.

- `catalog/entity/{Product,Category}.java` (Mongo `@Document`) → `domain/model/Product.java`,
  `Category.java` (framework-free POJOs, `id`/`categoryId`/`parentId` now `UUID` instead of
  Mongo ObjectId strings) + `infra/persistence/{ProductEntity,CategoryEntity}.java` (real
  JPA `@Entity`). `attributes`/`images`/`stock`/`attributeSchema` map to JSONB columns via
  Hibernate 6 native `@JdbcTypeCode(SqlTypes.JSON)` — no hand-written `AttributeConverter`
  needed.
- `catalog/entity/{ProductImage,StockEntry,CategoryAttribute}.java` → `domain/model/`
  (already framework-free, reused as-is inside `ProductEntity`/`CategoryEntity` — no
  duplicate infra copy, consistent with the `HsnGstRate`-adjacent value objects from
  earlier slices). `catalog/entity/{AttributeType,ProductStatus}.java` → `domain/enums/`.
- `catalog/repository/{ProductRepository,CategoryRepository}.java` (`MongoRepository`) →
  `infra/persistence/{ProductJpaRepository,CategoryJpaRepository}.java` (`JpaRepository`)
  — **temporarily public** (checkpoint 6a only; goes back to package-private like every
  other `*JpaRepository` once 6b's adapter lands). `ProductJpaRepository` also gets an
  interim `findActivePage(...)` query for `list()` — see pagination note below.
- `infra/persistence/{ProductMapper,CategoryMapper}.java` — package-private, both
  directions, unused by any service yet in 6a; proven via new round-trip tests
  `ProductMapperTest`/`CategoryMapperTest` (every field asserted, including nested
  `ProductImage`/`StockEntry`/`CategoryAttribute` lists and a null-`parentId` case).
- New Flyway migration `db/migration/postgresql/V3__catalog_schema.sql`: `categories`
  (JSONB `attribute_schema`, self-referencing `parent_id` FK) + `products` (JSONB
  `attributes`/`images`/`stock`, real FK `category_id → categories.id` — Postgres now
  enforces referential integrity Mongo never did). No H2 mirror, by design (catalog IT
  runs against real Postgres via Testcontainers).
- `../build.gradle`: removed `spring-boot-starter-data-mongodb` and
  `testImplementation 'org.testcontainers:mongodb'`; added
  `testImplementation 'org.testcontainers:postgresql'`. Confirmed via
  `./gradlew dependencies` — zero Mongo artifacts left on the runtime classpath.
- **Found and fixed during this checkpoint, not anticipated in the original plan:**
  Spring Boot scans every `@Entity` into one shared persistence unit regardless of which
  test uses it — so once `ProductEntity`/`CategoryEntity` existed, Hibernate's startup
  schema *validation* (`ddl-auto: validate`, inherited by the test profile from
  `application.yaml`) failed for **every** `@SpringBootTest`-based IT (Auth/User/Device/
  LoginHistory/Catalog), not just catalog's — because the H2 test schema has no
  `categories`/`products` tables (by design, no H2 mirror) even though those specific
  tests never query them. Fixed with one override in `application-test.yaml`:
  `spring.jpa.hibernate.ddl-auto: update` for the test profile only (production keeps
  `validate` against real Postgres). Hibernate auto-creates the two missing tables in H2
  at context startup; every Flyway-migrated table is untouched since it already matches.
- **Interim pagination note (explicit, not swept under the rug):** `ProductReader.list()`
  now orders by `id` ascending via the interim `findActivePage` query — but Postgres UUIDs
  are random, not insertion-ordered like Mongo's ObjectId was. This is **known and
  intentionally deferred to 6b's keyset cursor** (`(created_at, id)`), not a regression
  masked as done — `CatalogServiceMongoIT`, which asserted insertion-order pagination
  behavior specific to Mongo, was deleted in 6a rather than limping it along against a
  guarantee that no longer holds; 6b adds its Postgres-native successor
  (`CatalogServiceJpaIT`, already planned) once the real keyset logic exists.
- Dirty-checking audit for this checkpoint: **not yet applicable.** `CatalogServiceImpl`
  in 6a is still read-only (`listAll`/`getById`/`list`/`getDetail` — no entity is ever
  fetched-then-mutated-then-implicitly-relied-on-to-flush). The write paths
  (`SchemaDrivenProductFactory` building a new `ProductEntity`, `CatalogSeeder` calling
  `save()` explicitly) already save explicitly, same as before. This audit becomes live
  once 6b's `CatalogServiceImpl` (in `application/impl`, working through the domain port)
  exists — flagged to re-check then.
- `catalog/dto/response/{CategoryResponse,ProductDetailResponse}.java` — import fix only
  (now reference `domain/model`/`domain/enums`), not moved (that's 6b's `api/dto` move).
  `catalog/service/ProductFactory.java` — signature now returns `infra/persistence/
  ProductEntity` directly (interim; 6b decouples it from the repository lookup entirely
  and moves it to `domain/service`, returning `domain/model/Product`).
- `SchemaDrivenProductFactoryTest` rewritten for `CategoryJpaRepository`/`CategoryEntity`/
  `ProductEntity` and `UUID` category ids instead of Mongo-shaped String ids.
- 51/51 tests green (53 from Slice 5, minus 5 from the deleted `CatalogServiceMongoIT`,
  plus 3 new mapper-round-trip tests).

**Test infrastructure — H2 replaced with Testcontainers Postgres for every IT (2026-08-17).**
The `ddl-auto: update` patch above was a workaround, not a fix — per your instruction it's
now removed and replaced with the actual root-cause fix: every `@SpringBootTest` IT
(previously H2) now runs against a real Postgres instance via Testcontainers, schema created
by the real `db/migration/postgresql/*.sql` Flyway migrations, same as production. No more
hand-maintained H2 mirror to keep in sync — `db/migration/h2/` and the `com.h2database:h2`
test dependency are both deleted.
- `AbstractIntegrationTest`: `PostgreSQLContainer` started once in a static block (same
  singleton pattern already used for the jedis-mock Redis server) and wired via
  `@DynamicPropertySource` — one container for the whole test JVM, not one per test class.
- `application-test.yaml`: dropped the H2 datasource block and the `ddl-auto: update`
  override entirely; `validate` (inherited from `application.yaml`) now applies uniformly.
- Confirmed before finalizing, per your ask: full suite still 51/51 green, ~19.7s total on
  a fresh `--rerun` — same ballpark as the prior H2-based runs (17-23s across earlier
  slices). Only one Postgres container starts for the whole run (3 cached Spring contexts
  reuse it, matching the pre-existing context-caching behavior for differing `@MockBean`
  sets) — no meaningful slowdown.
- **Trade-off worth naming explicitly:** the test suite's earlier "no Docker required"
  property (documented in this file's Phase 0 section) no longer holds — Docker is now a
  hard requirement to run any `@SpringBootTest` IT at all, not just the catalog one. This
  was an explicit, informed choice (root-cause fix over a per-checkpoint workaround), not
  an incidental side effect.

**Slice 6, checkpoint 6b — ports/adapters, application/api layers, real pagination: DONE
(2026-08-17). Slice 6 (catalog) is now fully complete.**

- New `domain/port/{ProductRepository,CategoryRepository}.java`. `infra/persistence/
  {ProductJpaRepository,CategoryJpaRepository}.java` re-tightened back to package-private
  (the 6a "temporarily public" state is gone) — `infra/persistence/{ProductRepositoryAdapter,
  CategoryRepositoryAdapter}.java` are the only callers now, same shape as every other
  `*RepositoryAdapter` in the codebase.
- **Real Postgres keyset-cursor pagination**, replacing 6a's interim UUID-order query.
  New `domain/model/ProductPageCursor.java` (record `(createdAt, id)`, opaque
  base64url `encode()`/`decode()` — the wire-format-string-ness is deliberately confined to
  this one type, not scattered across the service/adapter). `ProductJpaRepository.
  findActivePage` now filters `(created_at, id) > (cursor.createdAt, cursor.id)` — the
  standard seek-pagination predicate (`createdAt > ? or (createdAt = ? and id > ?)`).
  New `domain/model/{ProductPage,ProductDetail}.java` — full DTO purity for
  `ProductReader`, which returns these instead of `ProductListResponse`/
  `ProductDetailResponse`. `ProductDetail` is a deliberate enriched read-model (category
  name + GST rate + derived `inStock` combined with `Product`) rather than bloating the
  core `Product` domain model with view-specific derived fields.
- **Found and fixed during this checkpoint, not anticipated in the plan:** the keyset
  query failed against real Postgres with `ERROR: could not determine data type of
  parameter $6` — a well-known Postgres/JDBC quirk where a bind parameter that only ever
  appears in a bare `? IS NULL` check (the "is this filter present" branch for
  `categoryId`/`brand`/`cursorCreatedAt`) can't have its type inferred at protocol-level
  parse time, even though the same named parameter is typed correctly everywhere else it's
  used. Fixed with explicit `cast(:param as <type>)` on each of the three optional-filter
  null-checks in `ProductJpaRepository`. Caught by the new `CatalogServiceJpaIT`
  integration test (see below) — a pure-mock test like `CatalogControllerIT` never would
  have surfaced this, since it never touches real Postgres wire protocol.
- `domain/service/{ProductFactory,SchemaDrivenProductFactory}.java` — moved from
  `catalog/service`, **signature decoupled**: `build(Category, ...)` instead of
  `build(String categoryId, ...)` with an internal repository lookup. The category lookup
  is now the caller's job (`infra/config/CatalogSeeder`), making the factory pure logic
  with zero infra dependency — matches the confirmed target design from the original
  mapping proposal.
- `infra/config/CatalogSeeder.java` — updated to fetch/create `Category` via the port
  first, then pass the domain object into the factory.
- `application/service/{CategoryReader,ProductReader}.java` (domain-typed, full purity) +
  `application/impl/CatalogServiceImpl.java` (moved from `catalog/service/impl`) — now
  works entirely through `domain/port` (`CategoryRepository`, `ProductRepository`,
  `HsnGstRateRepository`), no JPA repository or entity type anywhere in this class.
- `api/dto/response/{CategoryResponse,ProductDetailResponse,ProductListItemResponse,
  ProductListResponse}.java` → moved from `catalog/dto/response` unchanged. New
  `api/mapper/{CategoryMapper,ProductMapper}.java` build these from domain types;
  `ProductMapper` also owns the cursor string ↔ `ProductPageCursor` conversion, keeping
  `CatalogController` free of encoding concerns. `api/controller/CatalogController.java`
  moved from `catalog/controller`.
- **Found and fixed, a real correctness gap from the UUID id-scheme change:** path
  variables (`/categories/{id}`, `/products/{id}`) used to be loosely-typed Mongo ObjectId
  strings where any non-matching value cleanly fell through to a 404. With `UUID` ids, a
  malformed path value (e.g. the existing test's literal `"missing"`) would throw
  `IllegalArgumentException` from `UUID.fromString` — uncaught, that surfaces as a 500, not
  a 404. Fixed two ways: (1) `CatalogController` catches the parse failure at the two
  path-variable sites and rethrows the same `NotFoundException` shape as before (a
  malformed id can't match anything, so "not found" is correct); (2) `GlobalExceptionHandler`
  gained a general `IllegalArgumentException → 400 INVALID_REQUEST` handler for every other
  case (e.g. a malformed `category`/`cursor` query-filter param) — checked this doesn't
  change behavior anywhere else first (the only two existing `IllegalArgumentException`
  throw sites, `JwtCodec` and `GoogleTokenVerifier`, already catch it internally and never
  let it reach this handler).
- **Dirty-checking audit for this checkpoint (explicitly checked, not assumed):**
  `CatalogServiceImpl` remains fully read-only — no write path was added by 6b beyond what
  `CatalogSeeder` already did (explicit `save()` calls, same as 6a). No gap found.
- Old `catalog/` package (main + test) deleted entirely — catalog is now fully distributed
  across `domain/`, `application/`, `infra/`, `api/`, matching every other module.
- `SchemaDrivenProductFactoryTest` moved to `domain/service`, rewritten with zero
  repository mock (matches the decoupled factory — constructs a `Category` directly). The
  "unknown category" test case was dropped, not replaced: that scenario can no longer occur
  inside the factory now that the lookup lives in the caller, and no caller currently
  exercises "create product with unknown category" (no product-creation endpoint exists
  yet). New `ProductPageCursorTest` (round-trip + two malformed-input cases) and
  `CatalogServiceJpaIT` (moved to `application/impl`, the direct successor to the 6a-deleted
  `CatalogServiceMongoIT` — real keyset pagination across two pages, GST cross-lookup,
  stock-status derivation, all against the real Testcontainers Postgres every IT now
  shares).
- Confirmed via repo-wide `grep -rli mongo` (not just "should be gone"): zero remaining
  Mongo references anywhere except one historical-context Javadoc line in
  `CatalogServiceJpaIT` naming the class it replaced. Cleaned `application.yaml`
  (`spring.data.mongodb.*`), `application-test.yaml` (`management.health.mongo`), and
  `../docker-compose.yml` (the whole `mongo` service + its volume) — none of this had been
  touched in 6a.
- `PLAN_PHASE1.md` updated: data model section now describes the actual `categories`/
  `products` Postgres tables (JSONB columns, real FK, UUID ids) instead of the original
  Mongo document shapes; the outbox write path now describes an ordinary Postgres
  transaction instead of a Mongo multi-document transaction (called out explicitly as a
  genuine simplification, not just a find-replace); every other Mongo-specific mention
  (join keys, reindex backfill source, drift-reconciliation wording, Open Question #4's
  resolution) updated to Postgres. `INFRA.md`'s phase-to-infra table row for MongoDB
  replaced with a PostgreSQL JSONB row reflecting what's actually implemented; the
  Elasticsearch section's test-infra note updated now that every IT runs against real
  Postgres. Note: both docs still contain older stale package-path references (e.g.
  `auth/otp/OtpQueueConfig`, `config/RabbitConfig`) predating this whole migration and
  unrelated to Mongo — left alone as out of scope for this catalog-focused doc pass, not
  swept up opportunistically.
- **Known pending, explicitly out of scope for Slice 6:** `config/{OpenApiConfig,
  RabbitConfig,SecurityConfig}` still live at the old root `config/` package, never
  assigned to any of the six slices in this migration. Flagging so a future session
  doesn't assume the migration is 100% complete just because Slice 6 is done.
- 60/60 tests green (55 after 6b's core wiring, +1 `ProductPageCursorTest` malformed-input
  case added mid-checkpoint, +6 `CatalogServiceJpaIT`, net of the earlier 6a baseline).

## Final sweep and migration close-out (2026-08-17)

Before declaring the migration done, did a full-repo verification pass rather than
assuming Slice 6's completion meant everything was finished — this caught two real gaps.

**1. Root `config/{OpenApiConfig,RabbitConfig,SecurityConfig}` — moved to `infra/config/`.**
This was in the original mapping table under "Root-level / cross-module" but never
assigned to any of the six slices, so it never got executed. Pure package move, no
external references anywhere (Spring picks these up via component scan, nothing imports
them by name) — zero-risk relocation. `config/` (root) is now fully deleted.

**2. `common/exception/*`, `common/ApiError.java`, `common/GlobalExceptionHandler.java` —
never migrated, despite being explicitly confirmed back at the mapping-proposal stage.**
This was the more significant find. Your original decision (strict exception layering:
domain exceptions strip `HttpStatus`, `api/` owns the HTTP mapping) was confirmed before
Slice 1 even started, and I deliberately deferred it out of Slice 1's scope ("its own
atomic pass, not needed for the tax slice") — but then never actually came back to do it
in any of Slices 2 through 6. It sat unexecuted through the entire migration. Found only
because this final sweep traced every one of the original ~94 files to a concrete
disposition instead of trusting the slice-by-slice summaries above.

Executed now:
- `common/exception/ApiException.java` (base, carried `HttpStatus`) → `domain/exception/
  DomainException.java` — `HttpStatus` field removed entirely, keeps `code`/`message`
  only. The 6 subclasses (`BadRequestException`, `ForbiddenException`, `LockedException`,
  `NotFoundException`, `TooManyRequestsException`, `UnauthorizedException`) moved to
  `domain/exception/` with the same shape, now extending `DomainException`.
- `common/ApiError.java` → `api/dto/response/ApiError.java` (it's an HTTP response shape,
  per the original mapping's own reasoning — never actually applied until now).
- `common/GlobalExceptionHandler.java` → `api/GlobalExceptionHandler.java` (root of `api/`,
  no subfolder — matches the original proposal; `common/` and `api/` are the only two
  top-level areas with loose root classes rather than everything living in a subfolder).
  Gained a `Map<Class<? extends DomainException>, HttpStatus>` — the one place that now
  maps each business-rule-violation type to its wire-level status, replacing the
  `ex.getStatus()` call the old `ApiException` base class used to provide directly.
- Updated 24 files' imports across every layer (`api/controller/*`, `application/impl/*`,
  `domain/port/OtpRateLimiter.java` and others) — `domain/port` referencing exceptions
  that now live in `domain/exception` is itself a small pre-existing layering wart this
  fix incidentally corrects (it used to reference the ill-defined `common/exception`).
- Full suite still 60/60 green after this change, confirmed from a clean
  `./gradlew clean test` (not incremental) — the whole point of doing this as one pass
  rather than trusting each slice's "tests green" checkpoint in isolation.

**Full-repo verification performed, not assumed:**
- Every file from the original ~94-file pre-migration inventory traced to a concrete
  disposition (moved as-is, split into N files, or intentionally kept in `common/`) — the
  two gaps above are the only ones found; everything else matched what each slice's
  PROGRESS.md entry already claimed.
- `find src/main/java -type d -empty` and the same for `../src/test/java`: zero empty
  directories anywhere (one pre-existing empty `src/test/java/.../common/` dir, unrelated
  to this migration, was also cleaned up here since it was the same kind of leftover this
  sweep exists to catch).
- Every old package-by-feature directory (`auth/`, `user/`, `tax/`, `catalog/`, `device/`,
  `loginhistory/`, `config/`) confirmed **fully deleted**, not merely emptied, in both
  `../src/main/java` and `../src/test/java`.
- `grep -rli mongo` across the whole repo: zero matches except one historical-context
  Javadoc line naming a deleted class.

**All 6 slices of the hexagonal architecture migration are now complete, including the
two items above that had fallen through the cracks.** Every module (tax, auth/jwt,
auth/otp, user, auth/google, auth core, device, loginhistory, catalog) plus the
cross-cutting root config now lives in the target layered structure. No known gaps
remain — package root is `com.builddash.backend`.

## Final architecture snapshot

```
com.builddash.backend
├── api/                    — HTTP boundary. Nothing outside here builds an HTTP
│   │                          response shape or knows about REST/Swagger.
│   ├── controller/          AuthController, CatalogController, DeviceController,
│   │                        LoginHistoryController, UserController — call
│   │                        application/service interfaces, never application/impl
│   │                        or domain/port directly.
│   ├── dto/request/         Request bodies (bean-validation annotated).
│   ├── dto/response/        Response bodies, incl. ApiError.
│   ├── mapper/               domain/model ↔ api/dto conversion (AuthMapper,
│   │                        CategoryMapper, DeviceMapper, LoginEventMapper,
│   │                        ProductMapper, UserMapper). Owns any wire-format
│   │                        encoding decisions (e.g. ProductMapper's cursor
│   │                        string ↔ ProductPageCursor).
│   └── GlobalExceptionHandler.java — the one place a DomainException gets an
│                              HttpStatus.
│
├── application/            Use-case orchestration. Depends on domain/, never on
│   │                        infra/ directly (except plain @ConfigurationProperties
│   │                        beans — JwtProperties/OtpProperties — an accepted,
│   │                        deliberate exception: settings holders, not swappable
│   │                        behavior, so no port was worth inventing for them).
│   ├── service/              Use-case interfaces (AuthenticationFacade,
│   │                        CategoryReader, ProductReader, DeviceRegistry,
│   │                        RefreshTokenRotator, UserAccountService,
│   │                        UserProfileReader/Writer, LoginEventRecorder,
│   │                        LoginHistoryReader). Take/return domain/model or
│   │                        primitives only — never api/dto.
│   ├── impl/                 Their implementations (AuthServiceImpl,
│   │                        CatalogServiceImpl, DeviceServiceImpl,
│   │                        LoginEventServiceImpl, UserServiceImpl,
│   │                        OtpSendService, OtpVerificationService — the latter
│   │                        two have no separate interface, used concretely).
│   └── validator/            Bean Validation constraint + validator pair
│                              (ValidGstin/GstinValidator).
│
├── domain/                 Framework-free core. No Spring/JPA/JJWT/AMQP types
│   │                        anywhere except @Component on domain/service classes
│   │                        (pure logic, still needs DI wiring).
│   ├── model/                 POJOs/records: User, Device, LoginEvent, Product,
│   │                        Category + their value objects (ProductImage,
│   │                        StockEntry, CategoryAttribute), HsnGstRate,
│   │                        IssuedToken, TokenClaims, AuthSession, OtpSendResult,
│   │                        GoogleUserInfo, ProductPage/ProductDetail/
│   │                        ProductPageCursor (the enriched read-models + opaque
│   │                        cursor encoding for catalog).
│   ├── enums/                 TokenType, OtpMatchResult, GstinStatus,
│   │                        LoginEventType, ProductStatus, AttributeType.
│   ├── exception/              DomainException (no HttpStatus) + BadRequestException,
│   │                        ForbiddenException, LockedException, NotFoundException,
│   │                        TooManyRequestsException, UnauthorizedException.
│   ├── port/                  Interfaces infra/ implements: {User,Device,
│   │                        LoginEvent,Product,Category,HsnGstRate}Repository,
│   │                        TokenIssuer/TokenValidator, Otp{Sender,Store,
│   │                        RateLimiter,DispatchQueue}, PhoneExistenceIndex,
│   │                        GoogleIdentityGateway.
│   └── service/                Pure business logic needing no infra: OtpGenerator,
│                              ProductFactory/SchemaDrivenProductFactory (takes an
│                              already-resolved Category, no repository dependency).
│
├── infra/                  Everything that talks to the outside world. Implements
│   │                        domain/port; nothing above this layer imports infra/
│   │                        types except application/impl injecting a
│   │                        *Properties config bean (see the application/ note).
│   ├── persistence/            Split by kind, not by feature (changed 2026-08-18,
│   │                        superseding the flat per-feature layout every Slice
│   │                        1-6 entry below describes — kept as accurate history,
│   │                        not current structure):
│   │                          entity/    — JPA @Entity classes, public.
│   │                          mapper/    — entity ↔ domain/model *Mapper classes,
│   │                                       now public (was package-private under
│   │                                       the old flat layout).
│   │                          repository/ — *JpaRepository interfaces, now public
│   │                                       (same reason).
│   │                          adapter/   — *RepositoryAdapter classes, still
│   │                                       package-private within adapter/ —
│   │                                       implement domain/port, the only type
│   │                                       anything outside infra/persistence
│   │                                       ever references by name.
│   │                        **Trade-off, explicit:** the old "only Adapter is
│   │                        public surface" guarantee is gone — Entity/Mapper/
│   │                        JpaRepository are all public now, since a kind-based
│   │                        split can't keep them package-private to their
│   │                        adapter (different package). Nothing outside
│   │                        infra/persistence imports Entity/Mapper/JpaRepository
│   │                        types today regardless — the compiler just no longer
│   │                        stops it. Accepted knowingly, not an oversight.
│   ├── security/               JWT: JwtCodec (package-private), JwtTokenIssuer,
│   │                        JwtTokenValidator, JwtAuthenticationFilter,
│   │                        SecurityConfig.
│   ├── cache/                  Redis: RedisOtpStore, RedisOtpRateLimiter,
│   │                        BloomFilterPhoneExistenceIndex (in-memory, not
│   │                        actually Redis, but same "fast ephemeral lookup" role).
│   ├── messaging/               RabbitMQ publish side: RabbitOtpDispatchQueue,
│   │                        OtpDispatchMessage (wire payload).
│   ├── consumer/                RabbitMQ consume side: OtpDispatchListener.
│   ├── external/                 Outbound third-party calls: GoogleTokenVerifier,
│   │                        SmsOtpSender (dev-profile log stub).
│   └── config/                   Spring @Configuration + @ConfigurationProperties:
│                              JwtProperties, OtpProperties, OtpQueueConfig,
│                              PhoneExistenceIndexInitializer, CatalogSeeder,
│                              OpenApiConfig, RabbitConfig.
│
└── common/                 Genuinely cross-cutting, no single-layer owner.
    ├── AuthenticatedUser.java   Built by infra/security's JwtAuthenticationFilter,
    │                          consumed by every api/controller via
    │                          @AuthenticationPrincipal.
    └── Sha256.java              Generic hashing utility, no domain meaning.
```

**Known, deliberate exceptions to strict layering (all previously called out, not new):**
- `application/impl` injects `infra/config` `*Properties` beans directly (JwtProperties,
  OtpProperties) — settings holders, not behavior to invert behind a port.
- `domain/port/TokenValidator`'s implementation detail leak was already fixed (Slice 2) —
  it returns `domain/model/TokenClaims`, never JJWT's `Claims`.
- Nested value objects with zero framework annotation (`ProductImage`, `StockEntry`,
  `CategoryAttribute`, and every `domain/enums` type) live once in `domain/model`/
  `domain/enums` and are reused directly inside `infra/persistence` JPA entities — no
  duplicate infra-side copy, since duplicating an already-framework-free type buys nothing.

Package-by-feature is fully retired. Every new module going forward should land directly
in this five-package (`api`/`application`/`domain`/`infra`/`common`) structure from the
start, not be built package-by-feature and migrated later.



**Refined convention for scheduled jobs:**
- Any `@Scheduled` cron job or background poller must be placed in its owning module's `scheduler/` subpackage (e.g., `application/scheduler/`), rather than grouped globally or placed indiscriminately in `impl/`. `CatalogOutboxRelay` and `CatalogReindexer` were moved from `application/impl/` to `application/scheduler/` to enforce this. Future phases (like Phase 3's slot-expiry job) will follow this structure.

## Boilerplate Cleanup Pass
- Replaced 64 manual DI constructors across `api/`, `application/`, and `infra/` layers with `@RequiredArgsConstructor`.
- Ensured all injected dependencies assigned in those constructors are now explicitly declared `final`.
- Replaced 6 manual `LoggerFactory.getLogger` declarations with the `@Slf4j` annotation.
- Zero behavior change: this was a strict refactoring pass to remove boilerplate while keeping the full test suite green.

### Phase 3 Checkpoint A: Address & Geocoding
- **Migration:** `V8__create_addresses_table.sql` added.
- **Domain:** `Address` record, `GeocodingGateway`, `ServiceabilityGateway` and `AddressRepository` added.
- **Application:** `AddressService` built (CRUD, geocoding logic).
- **Infra:** Created `AddressEntity`, `AddressJpaRepository`, `AddressMapper`, `AddressRepositoryAdapter`, `GoogleMapsGeocodingAdapter` (stubbed for now), and `StubServiceabilityGateway`.
- **Testing:** `AddressServiceTest` and `AddressRepositoryAdapterJpaIT` implemented.

### Phase 3 Checkpoint B: Delivery Slot Locking & Generator (Concurrency Core)
- **Migration:** `V9__create_delivery_slots_tables.sql` added with `slot_configurations`, `delivery_slot_counters`, and `delivery_slot_locks`.
  - Added table-level check constraint `chk_slot_counter_capacity (current_count >= 0 AND current_count <= capacity)`.
  - Added unique constraint on `delivery_slot_counters(slot_id, slot_date)`.
- **Domain:** `SlotConfiguration`, `DeliverySlotCounter`, `DeliverySlotLock`, `DeliverySlotOption` records and `SlotUnavailableException` added.
- **Pessimistic Lock Scope:** `DeliverySlotCounterJpaRepository` defines exact single-row lock query:
  `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT c FROM DeliverySlotCounterEntity c WHERE c.slotId = :slotId AND c.slotDate = :slotDate") Optional<DeliverySlotCounterEntity> findBySlotIdAndSlotDateForUpdate(@Param("slotId") UUID slotId, @Param("slotDate") LocalDate slotDate);`
- **Application Scheduler:** `DeliverySlotGenerator` in `application/scheduler/` runs on schedule to pre-populate rolling future counter rows. Idempotent via `existsBySlotIdAndSlotDate` and unique constraint.
- **Application Service:** `DeliverySlotServiceImpl` implements `acquireOrSwapLock` in a single `@Transactional` boundary:
  - Single-row lock on target counter row.
  - Fail-closed if counter missing (`SlotUnavailableException`).
  - Capacity validation.
  - Decrement old slot counter and release old lock only if swap succeeds; rolls back old lock release if new slot acquisition fails.
- **Testing:** `DeliverySlotGeneratorTest`, `DeliverySlotServiceImplTest`, and `DeliverySlotLockingJpaIT` (multi-threaded race proof + rollback proof).

### Phase 3 Checkpoint C: Cart & Cart-Level Pricing
- **Migration:** `V10__create_cart_tables.sql` added:
  - Added `min_order_value` column to `coupons` table.
  - Added `carts` table with `user_id` and nullable `project_id` (enforcing uniqueness across (user_id, project_id)).
  - Added `cart_line_items` table with foreign key to `carts(id)` and unique constraint on `(cart_id, product_id)`.
- **Domain:** Added `Cart`, `CartLineItem`, `PricedCart`, `PricedCartLineItem` records and `CartPricingCalculator` port.
- **Application Services:**
  - `CartPricingCalculatorImpl`: Iterates line items through `PricingCalculator.calculate()`, aggregates totals, evaluates cart-level coupons against min-order-value / validity, and explicitly surfaces `couponDroppedReason` (e.g. `MIN_ORDER_VALUE_NOT_MET`, `COUPON_EXPIRED`) rather than dropping silently.
  - `CartServiceImpl`: Implements transactional cart mutation workflows (`getCart`, `upsertItem`, `removeItem`, `applyCartCoupon`, `removeCartCoupon`, `clearCart`).
- **Persistence:** Added `CartEntity`, `CartLineItemEntity`, `CartJpaRepository`, `CartLineItemJpaRepository`, `CartMapper`, `CartRepositoryAdapter`, and `CartLineItemRepositoryAdapter`.
- **Testing:** `CartPricingCalculatorTest` (parameterized test suite for min order value, expiration, and drop reasons) and `CartServiceImplTest` passing.

### Phase 3 Checkpoint D: Checkout Intent Orchestration & API
- **Domain:** Added `CheckoutIntent` domain model and `CheckoutValidationException`.
- **Global Error Handling:** Updated `GlobalExceptionHandler` with `Map.ofEntries` mapping domain exceptions (`SlotUnavailableException` -> 409, `CheckoutValidationException` -> 422, etc.).
- **Application Services:**
  - `CheckoutIntentServiceImpl`: Implements pre-payment orchestration:
    1. Re-validates live cart pricing using `PricingCalculator` (rejects if cart empty).
    2. Validates user address ownership and serviceability check.
    3. Validates cart price consistency against `expectedTotal`.
    4. Acquires/swaps 15-minute delivery slot lock atomically.
    5. Returns locked `CheckoutIntent`.
- **API Layer:**
  - DTOs: `CreateAddressRequest`, `UpsertCartItemRequest`, `ApplyCartCouponRequest`, `CreateCheckoutIntentRequest`, `AddressResponse`, `PricedCartResponse`, `PricedCartLineItemResponse`, `DeliverySlotOptionResponse`, `CheckoutIntentResponse`.
  - Mappers: `AddressDtoMapper`, `CartDtoMapper`, `DeliverySlotDtoMapper`, `CheckoutDtoMapper`.
  - Controllers:
    - `AddressController` (`/addresses`)
    - `CartController` (`/cart`)
    - `DeliverySlotController` (`/delivery-slots`)
    - `CheckoutController` (`/checkout`)
- **Testing:** Unit tests in `CheckoutIntentServiceImplTest` and integration tests in `CartControllerIT`, `CheckoutControllerIT` added.

## Status: Phase 3 — COMPLETE, 173/173 tests green

All 4 Phase 3 checkpoints (Address, Slot Locking/Generator, Cart & Pricing, Checkout Intent & API) are fully implemented and verified against the real PostgreSQL and Testcontainers test suite.
- **Test Suite Results:** 173/173 tests green (111 unit tests + 62 integration tests).
- **Concurrent Slot-Locking Proof:** `DeliverySlotLockingJpaIT` confirmed:
  - Multi-threaded race test (capacity 2, 10 concurrent threads) resulted in exactly 2 successful locks and 8 rejections with `SlotUnavailableException`.
  - Atomicity / Rollback test confirmed: failed swap preserves prior lock and counter without capacity leak.
- **End-to-End Composition:** `CheckoutControllerIT` confirmed full flow across Address, DeliverySlot, Cart, and Pricing composition with GST calculations.

## Status: Phase 4 (Payments & Order Core) — Checkpoints A & B COMPLETE

- **Checkpoint A (Order Creation & Persistence)**: Order, OrderLineItem entities, ports, and repositories implemented. `OrderService.create()` orchestrates saving the order and firing the gateway.
- **Checkpoint B (Idempotency)**: `Idempotency-Key` intercepting implemented. Concurrent replay protections verified.
- **Verification**: 
  - Manual E2E test via `POST /orders` complete. Normal create works.
  - Idempotency key replay test complete — returns identical order without duplication.
  - DummyPaymentGateway failure simulation complete (502 response, order remains `PAYMENT_PENDING`).
  - Tests (`OrderServiceImplTest`, `OrderControllerTest`, `OrderRepositoryAdapterJpaIT`, `IdempotencyKeyAdapterJpaIT`) implemented and committed.
- **Suite**: 183/183 tests green.

*(Note: Checkpoints C and D are NOT started yet. Orders currently enter `PAYMENT_PENDING` but cannot transition to `CONFIRMED` because the webhook service and sweep jobs do not yet exist.)*

## Status: Ongoing Audits

- **JWT_SECRET Leak Resolution**: The previously flagged issue of a real JWT_SECRET being committed to `.env.example` is resolved. Security fact-finding confirmed the value was local-only and never deployed (no CI/CD or deployment infrastructure exists in the repo). The secret was rotated locally, `.env.example` was scrubbed back to a dummy placeholder, and the application was confirmed operational against the new key. The remote git history containing the old secret was deliberately left as-is (no history rewrite) since the leaked value was never live and a rewrite would break existing clones/forks for no real security benefit.

- **Lombok Getter/Setter Cleanup (Phase 3 & 4)**: Audited code added after the Phase 1/2 cleanup pass (Address, Order, OrderLineItem, Payment, DeliverySlot, Cart, Checkout entities and DTOs). Replaced manually-written getters in `DomainException`, `GstRateUnresolvedException`, and `PaymentGatewayException` with `@Getter` annotations. Verified that no other manual getters/setters existed across all entities and DTOs created for Phase 3/4. Test suite remained 100% green.

## Status: Phase 4 (Payments & Order Core) — Checkpoint C COMPLETE

- **Checkpoint C (Webhook & Sweep)**:
  - Added `delivery_slot_lock_id` to `CheckoutIntent`, `Order`, and mapping layers (persisted in DB).
  - Implemented `PaymentWebhookServiceImpl` to transition `Order` to `CONFIRMED` upon `SUCCESS` and correctly mark `Payment` as `FAILED` on `FAILED`.
  - Implemented real HTTP `PaymentWebhookController` (`/api/webhooks/payment`) with an open Spring Security mapping.
  - Implemented `StaleOrderSweepServiceImpl` and `StaleOrderSweepJob` (intentional fallback to original planned name over `OrderSweepJob`). Used explicit `SELECT ... FOR UPDATE` via `OrderJpaRepository.findByIdForUpdate` to guarantee strict row-level lock against incoming webhooks before attempting to sweep a stuck order.
  - Ensured both webhook confirmation and sweep cancellation accurately release the delivery slot lock via the saved `deliverySlotLockId`.
- **Suite**: Full test suite green (184/184 tests pass), including real `PaymentWebhookControllerIT` via Spring `MockMvc`.

## Status: Phase 4 (Payments & Order Core) — Checkpoint D COMPLETE

- **Checkpoint D (Retry API & Webhook Race Test)**:
  - `retryPayment`: Implemented sequence: `findByIdForUpdate` lock -> `PAYMENT_PENDING` verify -> new `PENDING` payment row saved -> `gateway.initiate()` via `TransactionSynchronizationManager.registerSynchronization(afterCommit)`.
  - **Payment->Order 1:N discovery**: DB schema was already 1:N (no `UNIQUE` on `order_id`, no migration needed), but JPA layer assumed 1:1 (`findByOrderId` returned `Optional`, would throw `NonUniqueResultException` on retry). Fixed via `findFirstByOrderIdOrderByCreatedAtDescIdDesc` (deterministic tiebreak on id).
  - `InvalidOrderStateException` extended to accept `OrderStatus`, producing `ORDER_ALREADY_CONFIRMED` / `ORDER_ALREADY_CANCELLED` codes mapped to 409. Kept separate from the new `PaymentRetryInProgressException` (409, `RETRY_ALREADY_IN_PROGRESS`), since these are different failure modes (terminal state vs. in-flight retry).
  - `PaymentWebhookServiceImpl`'s `findLatestByOrderId` matching is a documented dummy-gateway simplification (comment in code) — real gateway integration should match by its own transaction ID once that field exists on the webhook payload.
  - `SweepVsWebhookJpaIT`: Structurally proves exactly one terminal state wins when sweep and webhook hit concurrently, loser takes its existing graceful path.
- **Suite**: Full test suite green. Test count: 184 -> 190.

*(Phase 4 is COMPLETE. Checkpoints A-D are all done with no open items.)*
