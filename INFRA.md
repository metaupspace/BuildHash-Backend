# Infrastructure — Phase Ownership

Maps each piece of infrastructure to the phase that introduced it, so later phases add only
what's new instead of redoing earlier work. Cross-reference with `PROGRESS.md` for feature status.

| Infra | Introduced | Status | Purpose | Reused later by |
|---|---|---|---|---|
| PostgreSQL | Phase 0 | Implemented | Durable relational data — users, devices, login events, HSN/GST rates | Every phase (products, orders, payments, etc. in later phases) |
| Redis | Phase 0 | Implemented | OTP hash storage (5-min TTL), send-rate/lockout counters | Session/rate-limit bookkeeping for any later phase that needs ephemeral state |
| PostgreSQL JSONB | Phase 1 | Implemented | Variable-schema catalog data (`products.attributes`, `categories.attribute_schema`) via Hibernate 6 native JSON mapping — no separate document store | Any later phase needing schema-flexible fields on an otherwise relational table |
| Bloom filter (Guava, in-memory) | Phase 0 | Implemented | Phone-existence pre-check on `POST /auth/otp/send` (`existingUser` hint in response) — avoids a Postgres round-trip just to decide "welcome back" vs "create account" copy | None planned yet — same `PhoneExistenceIndex` pattern (probabilistic pre-check backed by ground truth) is reusable wherever a cheap "have we seen X" signal beats a full DB lookup |
| RabbitMQ | Phase 0 | Implemented | Async OTP dispatch — `POST /auth/otp/send` publishes to the `otp.dispatch` queue instead of calling the SMS provider inline; a listener consumes and dispatches | **Phase 7 (Notifications)**: push/SMS/WhatsApp dispatch reuses the same broker + `Jackson2JsonMessageConverter` infra (`config/RabbitConfig`), new queues per channel. **Tally sync retry queue**: same broker, a dedicated queue with retry/DLQ semantics |
| Elasticsearch | **Not yet added** | Deliberately absent | Phase 1 (Catalog & Search): index products on catalog write, power fuzzy/multilingual search + autocomplete | — |

## Why each phase boundary is where it is

- **Bloom filter / RabbitMQ land in Phase 0, not later**: both are already load-bearing for
  `/auth/otp/send`'s actual behavior (the `existingUser` field and the dispatch path itself),
  not speculative infra staged ahead of need.
- **Elasticsearch stays out until Phase 1**: Phase 0 has no search/list/filter endpoints for it to
  power. Adding the dependency or a client bean now would be dead weight with nothing wired to
  it — the first real consumer is the Catalog module's product index.

## RabbitMQ — what Phase 0 built vs. what later phases add

Phase 0 owns:
- `config/RabbitConfig` — the single `Jackson2JsonMessageConverter` bean, shared by every queue
- `auth/otp/OtpQueueConfig` — declares the `otp.dispatch` queue (durable, default exchange)
- `auth/otp/OtpDispatchQueue` (interface) + `RabbitOtpDispatchQueue` (impl) — publish side
- `auth/otp/OtpDispatchListener` — consume side, delegates to the existing `OtpSender` abstraction
- `docker-compose.yml` `rabbitmq` service (management UI on `:15672`)

Phase 7 (Notifications) and the Tally sync retry queue should each declare their **own**
queue/config classes following this same shape (a narrow `*Queue` interface + impl + listener) —
they reuse the broker and the JSON converter, not Phase 0's OTP-specific classes.

## Elasticsearch — what Phase 1 will need to add (not started)

- `spring-boot-starter-data-elasticsearch` dependency
- `elasticsearch` service in `docker-compose.yml`
- An `application-{profile}.yaml` connection block (host/port/credentials via env vars, matching
  the existing pattern for Postgres/Redis/RabbitMQ)
- A `ProductSearchIndex`-style interface (index-on-write, search, autocomplete) — same
  interface-first pattern as everything in Phase 0, backed by an Elasticsearch client impl
- Test infra: every IT already runs against a real (Testcontainers) Postgres instance (see
  PROGRESS.md — the jedis-mock/RabbitMQ-bypass pattern still applies for those two), so ES is
  the one piece with no lightweight or already-proven Testcontainers story in this repo yet —
  needs its own decision when Phase 1 starts
