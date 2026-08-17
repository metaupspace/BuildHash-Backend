# BuildDash Backend — Progress

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
Fix, in `build.gradle`'s `test` task: `systemProperty 'api.version', '1.43'`. Verified with
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
   `src/main/java`, `src/main/resources`, `build.gradle` → zero matches. Traced
   `CatalogController.getProduct` → `CatalogServiceImpl.getDetail`: touches only
   `ProductRepository`/`CategoryRepository` (Mongo) and `HsnGstRateRepository` (Postgres/JPA).
   No ES client, no ES dependency in `build.gradle`. **Confirmed true** — Wave 3's
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
  scoped in the Phase 0 prompt" — grepping the actual codebase, `PROGRESS.md`, and `build.gradle`
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
- `build.gradle`: removed `spring-boot-starter-data-mongodb` and
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
  `docker-compose.yml` (the whole `mongo` service + its volume) — none of this had been
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
- `find src/main/java -type d -empty` and the same for `src/test/java`: zero empty
  directories anywhere (one pre-existing empty `src/test/java/.../common/` dir, unrelated
  to this migration, was also cleaned up here since it was the same kind of leftover this
  sweep exists to catch).
- Every old package-by-feature directory (`auth/`, `user/`, `tax/`, `catalog/`, `device/`,
  `loginhistory/`, `config/`) confirmed **fully deleted**, not merely emptied, in both
  `src/main/java` and `src/test/java`.
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
│   ├── persistence/            JPA @Entity classes ({User,Device,LoginEvent,
│   │                        Product,Category,HsnGstRate}Entity), package-private
│   │                        *JpaRepository interfaces, package-private *Mapper
│   │                        classes (entity ↔ domain/model, both directions where
│   │                        writes exist), and *RepositoryAdapter classes
│   │                        (implement domain/port, the only public surface).
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


