# Phase 1 — Catalog & Search: Plan

Planning only. No code, no scaffolding. Builds on Phase 0 conventions confirmed in
`PROGRESS.md`/`INFRA.md`: package-by-feature, narrow interfaces + DIP (Spring wiring, no
god-services), RabbitMQ as the standing async-decoupling mechanism (already proven for
OTP dispatch), and "introduce infra only when a real consumer needs it" (why ES was held
back from Phase 0).

---

## 1. Module Map

Two packages: `catalog/` and `search/`. Not one module — they have different data stores,
different consistency models, and different reasons to change (catalog = "what products
exist and their truth"; search = "how to find them fast"). Splitting keeps each SOLID:
a change to ranking/analyzers never touches product CRUD, and a change to category schema
never touches index mapping code.

**`catalog/`** — owns PostgreSQL (`categories`/`products` tables, JSONB for variable
attributes — see Section 2). Source of truth for Product/Category/Review/Q&A/Wishlist/
back-in-stock subscriptions. Depends on Phase 0's `tax` package (HSN/GST master, Postgres,
read-only) for the GST tag on product detail — matches the existing cross-module read
pattern (e.g. `auth` depending on `user`'s narrow interfaces, not reaching into its tables).
No separate document store: a Mongo-backed catalog was the original Phase 1 plan and was
actually built that way first, then migrated to Postgres/JSONB before Phase 1 shipped (see
PROGRESS.md) — variable attributes turned out not to need a dedicated document database,
and a single relational store for the whole backend removes a second datastore to operate.

**`search/`** — owns Elasticsearch. Read-optimized projection of catalog data plus its own
Postgres tables (`search_history`, trending aggregates) — these are search concerns per
SRP, not `user` concerns, even though `search_history` is per-user data.

**Boundary — how they talk:**
- `catalog → search`: **no synchronous call, no shared interface.** The only contract is
  an async event (Section 3). This is deliberate: it's the same shape as `auth.otp`'s
  `OtpSender`/`OtpDispatchQueue` split — the producer doesn't know or care who consumes.
- `search → catalog`: **none.** Search never reads catalog's Postgres tables directly. The event payload carries
  everything search needs to index (see Section 3, "fat event" decision) so search has
  zero runtime dependency on catalog's datastore. This is what keeps search genuinely
  extractable later if it ever needs to scale independently.
- `catalog` exposes one new narrow interface for **other future modules** (not search):
  a `ProductCatalogReader`-style interface (product-by-id, HSN code lookup) that Phase 2
  (Pricing) and Phase 3 (Cart) will depend on via DIP — mirrors `UserProfileReader`'s
  role in Phase 0.
- Both modules get their own controllers; nothing in `search`'s controller calls into
  `catalog`'s services or vice versa.

**Key interfaces per module (DIP, same discipline as Phase 0's OTP/JWT split):**
- `catalog`: `ProductFactory` (Factory — see Section 2, builds a valid `Product` document
  for a given category without a giant conditional in the service layer).
- `search`: `SearchIndex` (Adapter over the Elasticsearch client — the search service and
  the sync listener both depend on this, not on `RestHighLevelClient`/`ElasticsearchClient`
  directly, so an ES major-version bump or even a provider swap stays inside one impl
  class), `SearchQueryBuilder` (Builder — see Section 4), `ImageSearchProvider` (Adapter
  over whatever vision/matching API gets picked — see Open Questions #2). None of these
  are speculative: each wraps a real Phase 1 pain point (variable category schema,
  ES client coupling, multi-clause query construction, an unpicked third-party vendor).

---

## 2. Data Model

### PostgreSQL — `categories` table
```
id (UUID, PK), name, slug, parent_id (nullable, self-FK, tree structure),
attribute_schema (JSONB): [ { key, label, type (string|number|enum|boolean), required, unit? } ]
```
`attribute_schema` is the mechanism for "variable attributes per category" without
per-category code branches (OCP: a new category with new attributes = a new row,
zero new Java). Product-write validation and detail-page rendering both read this schema
generically instead of hardcoding "cement has bags-per-pallet, steel has diameter-mm".
Mapped via Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)` — no hand-written
`AttributeConverter` needed.

**Factory pattern — `ProductFactory`.** Assembling a `Product` is exactly the "one giant
conditional" risk the constraint calls out: cement fields aren't paint fields, and a
service method that branches on `categoryId` to decide which attributes to validate and
default is the god-method Phase 0's SOLID pass would have flagged. One interface,
`ProductFactory.build(Category, rawAttributes) -> Product` (domain/service — takes the
already-resolved `Category` domain object, not a `categoryId` string; the lookup is the
caller's job, keeping this pure logic with no repository dependency), sitting behind the
catalog write path. Default to a **single schema-driven implementation** that validates
`rawAttributes` against the category's `attributeSchema` generically (type-check each
key, enforce `required`) — this covers every category with zero per-category classes,
which is the right amount of abstraction for Phase 1 (YAGNI cuts against a factory-per-
category up front). Only if a specific category later needs bespoke construction logic
that the generic schema check can't express (a cross-field rule, a computed default) does
it get its own implementation behind the same `ProductFactory` interface — OCP holds
either way, and nothing is speculative-built for categories that don't need it yet.

### PostgreSQL — `products` table
```
id (UUID, PK), name, slug, category_id (UUID, real FK -> categories.id), brand,
hsn_code (string — matches Postgres hsn_gst_rates.hsn_code; NOT a DB-level FK, cross-table
  reference resolved at read time by the tax module's existing lookup),
attributes (JSONB): { <category's schema keys> : value },
images (JSONB): [ { url, alt, order } ],
stock (JSONB): [ { warehouseId, quantity } ]  — STUB for Phase 1 (Open Question #1, resolved):
  static/manually-seeded, no consumer wired to any vendor/warehouse event. No such system
  exists yet to publish from — building a consumer against an imagined contract now means
  redoing it once that system is real. Revisit when a real inventory source exists,
status (active|inactive|discontinued),
created_at, updated_at (updated_at doubles as the sync version — see Section 3)
```
`category_id` being a real foreign key (rather than an application-enforced reference) is
a genuine improvement over the original Mongo plan — Postgres now rejects a product
pointing at a nonexistent category at the database level, not just in application code.

Reviews, Q&A, and wishlist are **separate Postgres tables** (`reviews`, `questions`,
`answers`, `wishlist_entries`), each keyed by `product_id`/`user_id` FKs — not embedded in
`products`, so a hot review thread never bloats the row that search-sync and
product-detail reads touch on every request.

`Review` and `Answer` each carry a `status` (`pending|approved|rejected`) field, **defaulted
to `approved`** (Open Question #5, resolved: schema decision only). No moderation
workflow, queue, or admin UI is built in Phase 1 — the field exists purely so a moderation
feature later is a new consumer of an existing field, not a migration that back-fills a
status onto every historical review/answer.

### Elasticsearch — index mapping (accessed only via alias `products`, see Section 3)
```
productId      keyword         — join key back to Postgres products.id (UUID)
name           text (standard) + name.autocomplete (edge-ngram) + name.hi (Hindi analyzer)
nameAliases    text            — transliteration/alias table entries, same analyzer set as name
category       keyword + category.text
brand          keyword
attributes     flattened       — schema-free facet/filter field, mirrors the products.attributes
                                 JSONB column's variable shape without needing an ES mapping
                                 change per category
stockStatus    keyword         — derived (in_stock|out_of_stock), not raw quantities
                                 (avoids reindexing on every stock tick; stock itself is a
                                 Phase 1 stub, see Section 2/Open Question #1)
updatedAt      date            — external version source for the sync writes (Section 3)
```
No `price` field in Phase 1 — Pricing doesn't exist until Phase 2, and baking a price field
into the index now means either a placeholder or a second migration later. Sort-by-price on
`/products` can wait; `/search` ranks by relevance in Phase 1.

---

## 3. Postgres↔ES Sync Strategy (primary design decision)

**Transactional Outbox → RabbitMQ, fat event, external versioning, alias-based reindex.**
Reuses the RabbitMQ decoupling shape Phase 0 already validated for OTP dispatch. Not yet
built — see PROGRESS.md; `CatalogOutboxEvent`/`CatalogOutboxRelay` are planning only.

**Why plain dual-write is wrong here.** The obvious version — commit the Product change,
then publish the RabbitMQ message in the same service call — has a crash window: if the
process dies (or RabbitMQ is briefly unreachable) *after* the commit but *before* the
publish succeeds, the write is durable but the event is gone forever. Unlike OTP dispatch,
where a lost message just delays one SMS the user can retry, a lost catalog event means the
product silently never reaches search — no error, no retry, no visible symptom until
someone notices it's unsearchable. That gap is exactly what the **Outbox pattern** closes:
make the message durable *in the same transaction* as the domain write, so "the write
succeeded" and "the event will eventually be delivered" become the same fact instead of
two separate ones that can fall out of sync.

**Simpler than the original Mongo-based plan.** The original design needed a Mongo
**multi-document transaction** (requiring a replica set) just to write the `Product` change
and the outbox row atomically. On Postgres this is a single ordinary relational
transaction — `INSERT`/`UPDATE` into `products` and `INSERT` into `catalog_outbox_events`
in the same `@Transactional` method, no special infrastructure required. One fewer thing
to get right.

**Write path:**
1. Catalog write commits the `Product` change **and** inserts a `CatalogOutboxEvent` row
   (`aggregate_id`, `event_type`, `payload` = the ES-ready projection, `status=PENDING`,
   `created_at`) into `catalog_outbox_events`, in the same Postgres transaction — both
   succeed or both roll back. The API returns as soon as this transaction commits;
   correctness never depends on RabbitMQ or ES being reachable at request time.
2. A separate **relay/poller** (`CatalogOutboxRelay`, catalog-owned, polls `PENDING` rows
   — Postgres `LISTEN`/`NOTIFY` is the equivalent latency-optimization if polling delay
   ever matters, in place of the Mongo change-stream option from the original plan)
   publishes each row's payload to the `catalog.product.changed` queue
   with RabbitMQ publisher confirms, then flips the row to `PUBLISHED`. Because the row
   persists until confirmed, a relay crash mid-publish just means the next poll retries
   the same still-`PENDING`/`PUBLISHED`-unconfirmed row — nothing is lost, and republish
   is safe because the eventual ES upsert is idempotent (see versioning below).
3. The **catalog module still builds the message** — the full ES-ready projection, not
   just an id — for the same reason as before: search must never need to read Postgres to
   fill in a thin event, which is what makes the module boundary in Section 1 real.
4. A `search`-owned listener (own queue/config classes — per INFRA.md's instruction that
   later phases get their own queue classes rather than reusing Phase 0's OTP-specific
   ones) consumes and upserts into ES via the `products` alias, through the `SearchIndex`
   adapter (Section 1).
5. On successful ES index, the listener publishes a small `catalog.product.indexed`
   confirmation; a lightweight consumer on the catalog side (or the nightly reconciliation
   job, reusing infra rather than adding a second one) flips the outbox row to
   `PROCESSED`. This is what satisfies "mark processed on ES success" rather than just
   "handed to the broker" — the outbox row's terminal state reflects the thing that
   actually matters (searchable), not an intermediate transport milestone.

**Consistency:** eventual, not synchronous. Acceptable because:
- `/products/{id}` (detail) reads Postgres directly, not ES — the feature doc already calls
  for a short-TTL cache here because stock changes fast; ES staleness never leaks into a
  page that would otherwise show wrong stock/price.
- `/search` and `/search/suggest` are the only ES-backed reads, and a few seconds of lag
  between "product updated" and "shows up correctly in search" is a normal, accepted
  tradeoff industry-wide (this is not a payment or stock-decrement path).

**Ordering & idempotency:** RabbitMQ doesn't guarantee delivery order across retries.
Use Elasticsearch's built-in **external versioning** (`version_type=external`, version =
`updatedAt` as an epoch long) on every index write — ES rejects the write if the incoming
version isn't strictly greater than what's stored. This solves out-of-order delivery at
the datastore level instead of building app-side compare-and-swap logic, and upserts are
naturally idempotent on `productId`, so redelivery after an ack failure is safe.

**Failure handling:** unlike OTP dispatch (where a lost message just means one retry-able
SMS), a lost index event means a product silently never appears in search — worse because
it's invisible. The outbox already removes the worst failure mode (message never created
at all); the `catalog.product.changed` queue additionally gets its own DLQ from day one
(`x-dead-letter-exchange` + limited redelivery attempts), rather than deferring DLQ work
the way Phase 0 deferred it for OTP. A permanently-failed message lands in the DLQ and
gets logged/alerted; its outbox row stays `PUBLISHED`, never `PROCESSED`, so it's visible
to the reconciliation sweep below rather than silently disappearing.

**Deletes:** category/product deactivation publishes the same event type with
`status=inactive` (or a distinct `catalog.product.deleted`) so the listener issues an ES
delete/soft-hide rather than leaving a stale searchable ghost.

**Reindex on mapping change:** blue-green via alias, never an in-place mapping mutation
(ES doesn't allow changing an existing field's type in place anyway). Process: create
`products_v{n}`, backfill by paginating all Postgres products through the same
projection-building code used for live sync (one code path, not two), then flip the
`products` alias atomically once backfill catches up. Application/search code only ever
queries the alias name, never a versioned index name directly. Trigger: **nightly cron**
(Open Question #7, resolved) — `CatalogReindexer` is built against that trigger only; a
manual/admin-triggered variant can be added later as a second caller of the same
interface without changing it.

**Drift reconciliation:** eventual-consistency systems drift (crashed listener mid-batch,
a bug, manual Postgres edit, or an outbox row stuck at `PUBLISHED` because the confirmation
in step 5 above never arrived). The same nightly cron/blue-green mechanism doubles as both
the reindex machinery *and* the outbox sweep (any row still `PENDING`/`PUBLISHED` past a
threshold gets republished or force-marked `PROCESSED` once the reconciler confirms ES
actually has the current version). One job, two jobs' worth of drift covered — no separate
"targeted repair" code path to maintain. Revisit frequency/targeted-diff reconciliation
only if catalog size later makes a full nightly rebuild too slow — not a Phase 1 problem
(YAGNI at this scale).

---

## 4. API Contract Skeleton

**`catalog/`**
- `GET /categories`, `GET /categories/{id}`
- `GET /products?category=&brand=&price_min=&price_max=&sort=&cursor=` — cursor pagination,
  reads **Postgres, not ES** (Open Question #4, resolved)
- `GET /products/{id}` — detail incl. stock aggregate + HSN/GST tag, short-TTL cache
- `POST /products/{id}/notify-me` — back-in-stock subscription
- `GET /products/{id}/reviews`, `POST /products/{id}/reviews` — verified-purchase gated
- `GET /products/{id}/questions`, `POST /products/{id}/questions`,
  `POST /questions/{id}/answers` — `answer.source` distinguishes vendor/staff/customer
- `GET /users/me/wishlist`, `POST /users/me/wishlist`, `DELETE /users/me/wishlist/{productId}`
- `GET /products/{id}/related`, `GET /users/me/recommendations` — content-based/cold-start
  only in Phase 1; collaborative filtering deferred to a later phase (Open Question #3,
  resolved)

**`search/`**
- `GET /search?q=&lang=hi|en` — ES-backed, fuzzy + alias lookup. Built via
  `SearchQueryBuilder` (Builder): fuzzy match, Hindi/English alias lookup, category
  filter, and price-range filter are each **conditionally present** depending on which
  query params arrived — a single method with five optional params and nested `if`s to
  decide which ES clauses to add is exactly the sprawl the Builder avoids. The builder
  composes clauses one at a time (`.matchFuzzy(q).withAlias(lang).filterCategory(cat)...`)
  and only emits a final query with the clauses actually requested; same builder backs
  `/search/suggest`, minus the fuzzy/filter clauses, plus the autocomplete edge-ngram
  field.
- `GET /search/suggest?q=` — ES-backed, Redis-cached hot queries
- `GET /users/me/search-history`, `DELETE /users/me/search-history` — Postgres, capped last-N
- `GET /search/trending` — cached, populated by a background job aggregating query volume
- `POST /search/image` — **stub only** in Phase 1 per phase-plan; contract defined
  (accepts image upload, returns ranked SKU matches). Provider: **third-party vision/
  matching API**, not a self-hosted embedding model (Open Question #2, resolved — low-
  stakes given the adapter isolates the choice). Handler depends only on
  `ImageSearchProvider` (Adapter, Section 1), so the specific vendor is swappable behind
  that one interface without the controller or contract changing.

---

## 5. Open Questions — RESOLVED

All seven decided; implementation proceeds against these. Each decision is also inlined
at its point of relevance in Sections 2–4.

1. **Multi-warehouse stock read source — STUB.** `stock` on `Product` is a static/
   manually-seeded field for Phase 1, not a consumer of any real event. No vendor/
   warehouse system exists yet to publish from — building a consumer against an imagined
   contract now means redoing it once that system is real. Field carries a comment
   recording this decision (Section 2).
2. **Image search provider — third-party API.** Default to a third-party vision/matching
   API, not a self-hosted embedding model. Low-stakes call since `ImageSearchProvider`
   (Adapter) isolates it — proceed with the stub as already planned (Section 4).
3. **Recommendations scope — content-based only.** Confirmed: cold-start/content-based
   recommendations ship in Phase 1; collaborative filtering (and its nightly batch job)
   deferred to a later phase (Section 4).
4. **`/products` listing datastore — Postgres.** Confirmed: listing reads Postgres, not
   Elasticsearch (Section 4).
5. **Review/Q&A moderation — schema only.** `Review`/`Answer` get a `status`
   (`pending|approved|rejected`) field now, defaulted to `approved`. No moderation
   workflow, queue, or UI built in Phase 1 — schema decision only, to avoid a migration
   later (Section 2).
6. **ES test infrastructure — fake adapter + manual verification.** Unit-test `search`
   service logic against a fake `SearchIndex`, same pattern as `RecordingOtpDispatchQueue`
   in Phase 0; analyzer/fuzzy-match correctness verified manually via Swagger, matching
   Phase 0's SWAGGER_TEST_CASES.md convention. No Testcontainers-ES in Phase 1.
7. **Reindex trigger — nightly cron.** `CatalogReindexer` is built against a cron trigger
   only; a manual/admin-triggered variant can be added later as a second caller of the
   same interface without changing it (Section 3).
