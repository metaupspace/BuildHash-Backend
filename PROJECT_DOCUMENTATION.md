# BuildDash Backend — Project Documentation

**Generated from direct repository inspection.** Every claim below is either verified against source (build files, Java source, Flyway SQL, tests) or explicitly marked `Not found / not verifiable from repository`. Where a claim is inferred rather than read verbatim, it is labeled "inferred." This document does not describe planned or aspirational functionality — only what exists in the repository as of this inspection (`main`, commit `f3dcc55`, 2026-09-03).

---

## 1. Executive Overview

**What the system is.** BuildDash Backend (`com.builddash`, artifact `builddash-backend`, version `0.0.1-SNAPSHOT`) is a single-deployable Spring Boot 3.3.4 backend, written in **pure Java 21** (there is no Kotlin anywhere in the repository — `find . -name "*.kt"` returns zero results, despite prior session notes describing this as a Kotlin project). Package names (`domain/model` containing `Order`, `Product`, `Category`, `Cart`, `Invoice`, `Return`, `Refund`, `DeliverySlotLock`, `Company`, `Rfq`, `Statement`) and the shape of the API surface (catalog, cart, checkout, delivery slots, orders, payments, returns/QC, GST invoicing, B2B companies/RFQ/purchase-orders/approvals, monthly statements) indicate this is an e-commerce / building-materials procurement backend serving both direct consumers (B2C) and business buyers (B2B) in an Indian regulatory context (GST invoicing, HSN codes, DPDP data-protection compliance). No root `README.md` exists to supply an authoritative one-line description; this purpose statement is inferred from code structure and `docs/builddash-backend-phase-plan.md` / `docs/builddash-backend-side-features-detailed.md`.

**Business/domain scope.** Product catalog with category-driven variable attributes, Q&A/reviews/wishlist, a pricing engine (base price → bulk tiers → user/company contract pricing → margin floor → GST → coupons), guest and authenticated shopping carts, delivery-slot-scheduled checkout, order lifecycle through delivery, returns with photo evidence and QC-gated refunds, GST-compliant tax invoicing with fiscal-year-scoped sequential numbering, a full B2B subsystem (companies, sites, per-company RBAC, RFQ→vendor-quote→conversion, bulk PO import, spend-threshold approval gating with escalation), monthly B2B statements (PDF+XLSX), support tickets with SLAs, and DPDP-driven data export/account deletion.

**Architectural style.** Hexagonal / ports-and-adapters, verified directly from the package layout under `src/main/java/com/builddash/backend/`:
- `api/` — inbound adapter: controllers, request/response DTOs, mappers.
- `application/` — use-case orchestration: `service` (64 interfaces), `impl` (59 implementations), `scheduler` (13 background jobs), `event`/`listener` (in-process domain events), `validator`.
- `domain/` — framework-free core: `model` (84 classes, mostly immutable records), `port` (91 interfaces — every outbound dependency the application layer needs), `service` (9 pure business-rule classes), `enums` (43 closed vocabularies), `exception` (39 business-rule violation types).
- `infra/` — outbound adapters implementing `domain/port` interfaces: `persistence` (JPA), `security`, `gateway`, `external`, `messaging`/`consumer` (RabbitMQ), `search` (Elasticsearch), `storage` (S3), `pdf`/`excel` (document generation), `crypto` (PII encryption), `cache` (Redis), `config`, `seed`.

Dependency direction is strictly inward: `infra` depends on `domain`/`application`; `domain` depends on nothing else in the codebase. This is a single-module Gradle build (`settings.gradle` declares one root project) — the hexagonal boundary is enforced by package convention and code review discipline, not by separate Gradle modules or `internal`-style visibility (Java has no such feature); adapter classes are frequently declared package-private as a partial substitute.

**Runtime model.** One Spring Boot process serving synchronous HTTP (`spring-boot-starter-web`), STOMP WebSocket (`spring-boot-starter-websocket`, for live order-tracking), a `@Scheduled` in-JVM job scheduler (13 background jobs, no Quartz/ShedLock), and RabbitMQ consumers (`spring-boot-starter-amqp`) for OTP dispatch, notification dispatch, and catalog→search sync. There is no separate worker process — schedulers and queue consumers run inside the same JVM as the HTTP server.

**Current production-readiness state (headline).** The codebase shows extensive, deliberate concurrency and financial-integrity hardening (documented inline via commit-referenced comments like "H0.1", "H2.5", "H9.1" tracing back to a phased hardening program). However, **the payment gateway integration is a stub** (`DummyPaymentGatewayAdapter`, `@Profile("!prod")`, no real provider implementation exists anywhere in the repository), and of 13 external-system adapters in `infra.external`/`infra.gateway`, only **2** (`GoogleTokenVerifier` for Google Sign-In, `SmtpEmailSender` for email) have genuine third-party wiring — OTP SMS, push/SMS/WhatsApp notifications, geocoding, address serviceability, masked calling, and image search are all dev-stub-only with no production adapter. Full evidence-based assessment is in §27 (Production Readiness).

**Major external dependencies (infrastructure, not code-level libraries):** PostgreSQL 16, Redis 7, RabbitMQ 3, Elasticsearch 8.15, S3-compatible object storage (MinIO in dev). See §16/§23 for what's implemented vs. what deployment infrastructure must supply.

**Major technical constraints:**
- No `@Version` (optimistic locking) anywhere — all concurrency safety is pessimistic locks, CAS conditional updates, native upserts, or unique/exclusion constraints (see §19).
- No ShedLock/Quartz — multi-instance scheduler safety relies entirely on database-level atomicity (row locks, conditional UPDATEs, unique constraints), documented explicitly in scheduler Javadocs.
- No API versioning scheme, no global pagination envelope (ad hoc `page`/`size` query params per endpoint returning bare lists).
- No CI/CD configuration in the repository (no `.github/workflows`, no `Jenkinsfile`, etc.) — testing/build must be run manually or by external infrastructure not present here.
- No Dockerfile for the application itself — `docker-compose.yml` provisions only infrastructure dependencies (Postgres, Redis, RabbitMQ, Elasticsearch, MinIO, pgAdmin); the Spring Boot app runs via `./gradlew bootRun` or a built JAR.

---

## 2. Technology Stack

All versions below are copied verbatim from `build.gradle` and `gradle/wrapper/gradle-wrapper.properties` — none are inferred.

| Technology | Exact version | Purpose | Where configured | Scope | Notes |
|---|---|---|---|---|---|
| Java | 21 | Language/runtime | `build.gradle` `java.toolchain.languageVersion` | build/runtime | `JavaLanguageVersion.of(21)`; pure Java, no Kotlin |
| Gradle (wrapper) | 9.7.1 | Build tool | `gradle/wrapper/gradle-wrapper.properties` | build | A stray `.gradle/9.7.0` cache dir exists locally but the pinned wrapper version is 9.7.1 |
| `org.springframework.boot` (plugin) | 3.3.4 | Spring Boot Gradle plugin (packaging, dependency BOM) | `build.gradle` | build | |
| `io.spring.dependency-management` (plugin) | 1.1.6 | Spring BOM-managed transitive versions | `build.gradle` | build | |
| `java` (plugin) | built-in | Java compilation | `build.gradle` | build | No Kotlin plugin present |
| Spring Boot | 3.3.4 | Application framework | plugin version | runtime | |
| `spring-boot-starter-web` | BOM | REST controllers, embedded Tomcat, Jackson | `build.gradle` | runtime | Powers `api/controller` |
| `spring-boot-starter-websocket` | BOM | STOMP/WebSocket | `build.gradle` | runtime | Live order-tracking (`/ws`, `WebSocketConfig`) |
| `spring-boot-starter-data-jpa` | BOM | JPA/Hibernate ORM, Spring Data repositories | `build.gradle` | runtime | Backs `infra/persistence/{entity,repository}` |
| `spring-boot-starter-data-redis` | BOM | Redis client (Lettuce) | `build.gradle` | runtime | OTP store, rate limiting, Bloom-filter init |
| `spring-boot-starter-validation` | BOM | Jakarta Bean Validation | `build.gradle` | runtime | `@Valid` DTOs in `api/dto` |
| `spring-boot-starter-security` | BOM | Spring Security | `build.gradle` | runtime | Backs `infra/security`, `SecurityConfig` |
| `spring-boot-starter-actuator` | BOM | Health/metrics | `build.gradle` | runtime | Only `health` endpoint exposed |
| `org.postgresql:postgresql` | BOM | PostgreSQL JDBC driver | `build.gradle` (`runtimeOnly`) | runtime | |
| `io.jsonwebtoken:jjwt-api` | 0.12.6 | JWT API | `build.gradle` | runtime | |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 | JJWT implementation | `build.gradle` (`runtimeOnly`) | runtime | |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.6 | JJWT Jackson support | `build.gradle` (`runtimeOnly`) | runtime | |
| `com.google.api-client:google-api-client` | 2.7.0 | Google ID-token verification | `build.gradle` | runtime | Real Google Sign-In integration (`GoogleTokenVerifier`) |
| `org.flywaydb:flyway-core` | BOM | Migration engine | `build.gradle` | runtime | |
| `org.flywaydb:flyway-database-postgresql` | BOM | Flyway Postgres dialect | `build.gradle` (`runtimeOnly`) | runtime | Required for Flyway 10+ |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.6.0 | OpenAPI/Swagger UI | `build.gradle` | runtime | Gated by `springdoc.swagger-ui.enabled` |
| `spring-boot-starter-amqp` | BOM | RabbitMQ (Spring AMQP) | `build.gradle` | runtime | Backs `infra/messaging`, `infra/consumer` |
| `spring-boot-starter-data-elasticsearch` | BOM | Elasticsearch client | `build.gradle` | runtime | Backs `infra/search` |
| `com.google.guava:guava` | 33.1.0-jre | Utility library | `build.gradle` | runtime | Bloom filter (`BloomFilterPhoneExistenceIndex`) |
| `com.github.librepdf:openpdf` | 2.0.3 | PDF generation | `build.gradle` | runtime | Invoice + statement PDF rendering |
| `software.amazon.awssdk:s3` | 2.25.16 | AWS S3 SDK v2 | `build.gradle` | runtime | `infra/storage`, S3-compatible (MinIO in dev) |
| `spring-boot-starter-mail` | BOM | SMTP/JavaMail | `build.gradle` | runtime | Statement email delivery (prod SMTP; non-prod logs) |
| `org.apache.poi:poi-ooxml` | 5.3.0 | XLSX parsing/generation | `build.gradle` | runtime | PO bulk import (streaming SAX parse, deliberately avoids full in-memory load) + statement XLSX (SXSSF streaming write) |
| `org.projectlombok:lombok` | BOM | Boilerplate reduction | `build.gradle` (`compileOnly`/`annotationProcessor`) | build | |
| `spring-boot-starter-test` | BOM | JUnit 5, AssertJ, Mockito, Spring Test | `build.gradle` (`testImplementation`) | test | |
| `spring-security-test` | BOM | Security test support | `build.gradle` | test | |
| `com.github.fppt:jedis-mock` | 1.1.9 | In-memory Redis (RESP server) | `build.gradle` | test | Real Lettuce client against a fake server |
| `org.testcontainers:junit-jupiter` | BOM | Testcontainers JUnit 5 | `build.gradle` | test | |
| `org.testcontainers:postgresql` | BOM | Real Postgres container | `build.gradle` | test | |
| `org.testcontainers:minio` | 1.20.1 | Real MinIO container | `build.gradle` | test | |
| `org.junit.platform:junit-platform-launcher` | BOM | JUnit platform launcher | `build.gradle` (`testRuntimeOnly`) | test | |

**Logging**: no explicit logging dependency; Spring Boot's default Logback (transitive via `spring-boot-starter-web`) applies. No `logback-spring.xml` found — default pattern output. `logging.level.com.builddash.backend: DEBUG` set only in `application-dev.yaml`.

**Metrics/observability**: `spring-boot-starter-actuator` present but only `health` is exposed (`management.endpoints.web.exposure.include: health`). No Micrometer registry / Prometheus dependency. **Not found / not verifiable**: no metrics export beyond actuator health.

**HTTP client**: no `WebClient`/reactive stack (`spring-webflux` not declared). Outbound HTTP (Google token verification, AWS SDK, SMTP) rides on each SDK's own transport.

**Test task configuration** (`build.gradle`, lines ~67–73): `useJUnitPlatform()` is declared explicitly; `systemProperty 'api.version', '1.43'` works around a Testcontainers/Docker Engine API-version negotiation incompatibility (documented inline: "Testcontainers' docker-java client hardcodes API v1.32 for its initial strategy probe... recent Docker Engine/Desktop (MinAPIVersion 1.40+) rejects with 400"). No separate Gradle source set for integration tests — `*IT.java` and `*Test.java` coexist in `src/test/java` under the single `test` task, distinguished only by naming convention.

**Password hashing**: **not present in this codebase** — there is no `PasswordEncoder`/BCrypt anywhere in `src/main/java` (verified by repo-wide grep). Authentication is exclusively OTP (phone) and Google Sign-In (see §8); there is no password-based login to hash a password for.

---

## 3. Repository Map

| Path | Purpose |
|---|---|
| `build.gradle` | Gradle build script — plugins, dependencies, Java 21 toolchain, test task config |
| `settings.gradle` | Single-module root project name (`builddash-backend`) |
| `gradlew`, `gradlew.bat` | Gradle wrapper launchers |
| `gradle/wrapper/` | Wrapper JAR + `gradle-wrapper.properties` (Gradle 9.7.1) |
| `docker-compose.yml` | Local dev infrastructure: Postgres, pgAdmin, Redis, RabbitMQ, Elasticsearch, MinIO |
| `.env` / `.env.example` | Local dev environment variables / template. `.env.example` declares `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` only — it does **not** cover `PAYMENT_WEBHOOK_SECRET`, `PII_MASTER_KEY`, `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, so a checkout relying solely on `.env` cannot boot the non-dev profile (these fail closed with no default) |
| `src/main/java/com/builddash/backend/` | Application source — see §4/§5 |
| `src/main/resources/` | `application.yaml`, `application-dev.yaml`, `application-test.yaml`, `db/migration/postgresql/` (41 Flyway migrations) |
| `src/test/java/com/builddash/backend/` | 245 test source files across `api/`, `application/`, `domain/`, `infra/`, `support/` |
| `docs/` | Project planning/progress docs: phase plans (`PLAN_PHASE1.md`–`PLAN_PHASE8.md`), `PROGRESS.md` (cumulative history log), `INFRA.md` (partially stale infra tracker), `SWAGGER_TEST_CASES.md`, `CLAUDE.md` (AI-assistant behavioral guidance, not app documentation) |
| `bin/`, `build/`, `.gradle/` | Generated build output — **excluded** from documentation detail; regenerated by the build, not source of truth |
| `graphify-out/` | Output of a third-party code-graphing tool used as a navigation aid for *producing* this documentation — **not part of the application** |
| `.idea/`, `.vscode/` | IDE metadata — **excluded**; confirms module names `main`/`test` but carries no independent architectural information |
| `.claude/`, `.remember/` | AI-assistant tooling configuration/session state — **excluded** from application architecture |
| `test_output.log` (242KB) | Stale captured test-run console output sitting at repo root — not a maintained project file; its presence outside `build/`/`.gitignore` is a minor repo-hygiene note (see §28) |

---

## 4. Complete Package Map

### `api` layer (inbound adapter)
| Package | Purpose | Layer | Depends on | Notes |
|---|---|---|---|---|
| `api.controller` (+ `api.controller.order`) | 33 HTTP entry-point classes | Adapter (inbound) | `application.service`, `api.dto`, `api.mapper` | Deliberately thin: extract principal/params → delegate to one service interface → map via `api.mapper`. No business logic or authorization branching in controllers — HTTP authz lives entirely in `SecurityConfig`; fine-grained/B2B authz lives in service `Impl` classes. Order/payment/return controllers nest under `api.controller.order`; every other controller is flat. |
| `api.dto.request` | 33 request DTOs (Java records), Jakarta Bean Validation annotations directly on record components | Adapter DTO | — | |
| `api.dto.response` | 55 response DTOs (records); smaller subset routes through `api.mapper`, most use static `from(...)` factories | Adapter DTO | — | |
| `api.mapper` | 20 domain→DTO translators, one per controller's aggregate | Adapter | `domain.model` | Manual mapping, no MapStruct |
| `api` (root) | `GlobalExceptionHandler`, `ApiError` | Cross-cutting | `domain.exception` | Single seam mapping the entire domain exception hierarchy to HTTP |

### `application` layer (use-case orchestration)
| Package | Purpose | Layer | Depends on | Notes |
|---|---|---|---|---|
| `application.service` | 64 narrow interfaces (CQRS-flavored read/write splits for several aggregates, e.g. `QnaReader`/`QnaWriter`) | Inbound port boundary | `domain.model`, `domain.enums`, `domain.port` (types only) | |
| `application.impl` | 59 `@Service` implementations — transaction boundaries, lock ordering, orchestration live here | Use-case layer | `domain.port`, other `application.service` interfaces, `application.event` | Interface/impl split consistently followed; a few concrete classes have no interface at all by deliberate "single workflow, single caller" design (`OtpSendService`, `CatalogReindexer`) |
| `application.scheduler` | 13 `@Scheduled`/`@RabbitListener` background jobs | Driving adapter (time/queue-triggered) | `application.service`, some `domain.port` directly | No ShedLock/Quartz — DB-level atomicity is the only multi-instance safety net |
| `application.event` | 13 record DTOs, ids-only payloads, published via `ApplicationEventPublisher` | Internal event vocabulary | none | Listeners re-fetch current state rather than trust the payload |
| `application.listener` | 2 `@TransactionalEventListener(AFTER_COMMIT)` handlers | Event consumer | `application.event`, `application.service` | Each handler also `@Transactional(REQUIRES_NEW)` — runs only after the triggering transaction durably commits, in its own transaction |
| `application.validator` | `@ValidGstin` + `GstinValidator` | Cross-cutting validation | Jakarta Validation SPI | Format-only regex, no live GST-portal check |

### `domain` layer (framework-free core)
| Package | Purpose | Layer | Notes |
|---|---|---|---|
| `domain.model` | 84 classes — mostly immutable records, some Lombok POJOs (`User`, `Product`, `Category`, `Device`, `Review`, etc.) | Domain core | No JPA imports anywhere (verified) — parallel to `infra/persistence/entity`, converted at the adapter boundary via `infra/persistence/mapper` |
| `domain.port` | 91 interfaces — every outbound dependency: persistence, gateways, messaging, rendering, storage, search, security/config | Hexagonal boundary | Every implementation lives in `infra/*` (two exceptions: `CartPricingCalculator` and `UserDataExporter` are implemented in `application/impl`, since they're pure orchestration with no external I/O of their own) |
| `domain.service` | 9 stateless/pure classes (coupon validation, refund math, statement period math, OTP generation, product-attribute-schema validation, B2B default permissions, catalog→search projection, answer-source resolution) | Pure business logic | No repository/I/O access — orchestration needing persistence lives in `application/impl` |
| `domain.enums` | 43 closed vocabularies, several with substantial Javadoc explaining deliberate incompleteness (e.g. `SupportTicketStatus`) | Domain vocabulary | |
| `domain.exception` | 39 classes rooted at abstract `DomainException` (`code` + message, **no HTTP status** by design — mapped externally in `api/GlobalExceptionHandler`) | Business-rule violations | Two outliers (`AmbiguousGatewayException`, `GatewayRejectedException`) extend `RuntimeException` directly |

### `infra` layer (outbound adapters)
| Package | Purpose | Key classes |
|---|---|---|
| `infra.persistence.{entity,repository,adapter,mapper,order,converter}` | JPA layer — see §10 | 58 entities, 57+3 repositories, 55 adapters, 29 mappers |
| `infra.security` | JWT mechanics, filters, WebSocket auth | `JwtCodec`, `JwtTokenIssuer`, `JwtTokenValidator`, `JwtAuthenticationFilter`, `RateLimitFilter`, `WebSocketAuthChannelInterceptor`, `ClientIpResolver` |
| `infra.gateway` | Payment + call-proxy gateway adapters | `DummyPaymentGatewayAdapter`, `DummyCallProxyGatewayAdapter` — **both stubs, no prod implementation** |
| `infra.external` | 11 third-party/stub adapters | See §16 |
| `infra.messaging` / `infra.consumer` | RabbitMQ publish/consume | See §15 |
| `infra.config` | 20 `@Configuration`/`@ConfigurationProperties` classes | `SecurityConfig`, queue configs, `SchedulingConfig`, `WebSocketConfig`, `OpenApiConfig`, property classes |
| `infra.storage` | S3-compatible object storage | `S3ObjectStorageAdapter` |
| `infra.search` | Elasticsearch | `ElasticsearchSearchIndexAdapter`, `...AdminAdapter`, `...QueryGatewayAdapter`, `SearchQueryBuilder` |
| `infra.pdf` / `infra.excel` | Document generation | `OpenPdfInvoiceRenderer`, `OpenPdfStatementRenderer`, `SxssfStatementWorkbookWriter`, `PoWorkbookParserAdapter` |
| `infra.crypto` | PII field encryption | `PiiCipher`, `HmacIndex`, `ConfigPiiKeyProvider`, `PiiCryptoHolder` |
| `infra.cache` | Redis-backed rate limiting/OTP/Bloom filter | `RedisRateLimiter`, `RedisOtpRateLimiter`, `RedisOtpStore`, `FixedWindowCounter`, `BloomFilterPhoneExistenceIndex` |
| `infra.seed` | Dev-only data seeders | `CatalogSeeder`, `CompanySeeder` (`@Profile("dev")`) |
| `common` | Cross-layer shared types | `AuthenticatedUser` (Spring Security principal record), `Sha256` |

---

## 5. Complete File Catalogue

**Scoping note** (per the documentation brief's own allowance to summarize large groups rather than omit them): with 1,098 Java source files, a literal one-row-per-file table for every DTO, mapper, entity, and repository would be tens of thousands of rows of low-information restatement of a filename. This section instead provides: (a) full per-file detail for every architecturally significant class (controllers, service impls, schedulers, adapters with concurrency logic, domain state machines, security/config classes — several hundred files, detailed in §7–§15 and §19), and (b) **complete filename enumeration** for every package, grouped, so no file is omitted from the record even where its one-line responsibility is adequately implied by its name and package. Trivial generated/build artifacts (`bin/`, `build/`, `.gradle/`) are excluded because they are regenerated by the build and carry no independent source-of-truth content — see §3.

### Controllers (33 files) — full detail in §7
AddressController, ApprovalController, ApprovalPolicyController, AuthController, CartController, CatalogController, CheckoutController, CompanyController, CompanyMemberController, CompanyRolePermissionController, CompanySiteController, DeliverySlotController, DeviceController, LoginHistoryController, NotifyMeController, `order/InvoiceController`, `order/OrderController`, `order/PaymentWebhookController`, `order/RefundWebhookController`, `order/ReturnController`, PoAttachmentController, PoImportController, QnaController, ReviewController, RfqController, RfqQuoteAdminController, SearchController, SearchHistoryController, StatementController, SupportChatController, SupportTicketController, UserController, VendorAdminController, WishlistController.

### API Mappers (20 files)
AddressDtoMapper, AuthMapper, CartDtoMapper, CategoryMapper, CheckoutDtoMapper, DeliverySlotDtoMapper, DeviceMapper, InvoiceDtoMapper, LoginEventMapper, NotifyMeMapper, OrderDtoMapper, OrderTrackingDtoMapper, ProductMapper, QnaMapper, ReturnDtoMapper, ReviewMapper, SearchMapper, SupportDtoMapper, UserMapper, WishlistMapper.

### Request DTOs (33 files)
AddWishlistItemRequest, AdminQuoteSubmitRequest, AnswerQuestionRequest, ApplyCartCouponRequest, ApprovalPolicyRequest, AskQuestionRequest, ChatRequest, CompanyCreateRequest, CompanyMemberRequest, CompanySiteRequest, CreateAddressRequest, CreateCheckoutIntentRequest, CreateOrderRequest, CreateReturnRequest, CreateSupportTicketRequest, DelegateApprovalRequest, DeliveryStatusUpdateRequest, GoogleSignInRequest, OtpSendRequest, OtpVerifyRequest, PaymentWebhookRequest, RefreshRequest, RefundWebhookRequest, ReplaceRolePermissionsRequest, RescheduleOrderRequest, ReturnLineItemRequest, RfqConvertRequest, RfqCreateRequest, SubmitReviewRequest, SupportTicketMessageRequest, UpdateProfileRequest, UpsertCartItemRequest, VendorCreateRequest, VendorUpdateRequest.

### Response DTOs (55 files) + `ApiError`
AddressResponse, AnswerResponse, ApprovalPolicyResponse, ApprovalResponse, AuthTokensResponse, CallDriverResponse, CategoryResponse, ChatResponse, CheckoutIntentResponse, CompanyMemberResponse, CompanyResponse, CompanySiteResponse, DeleteRequestResponse, DeliverySlotOptionResponse, DeviceResponse, DriverDto, ImageSearchResponse, InvoiceResponse, LocationDto, LoginEventResponse, NotifyMeSubscriptionResponse, OrderLineItemResponse, OrderResponse, OrderTrackingResponse, OtpSendResponse, PoAttachmentResponse, PoImportConvertResponse, PoImportResponse, PoImportRowResponse, PricedCartLineItemResponse, PricedCartResponse, ProductDetailResponse, ProductListItemResponse, ProductListResponse, ProductSearchHitResponse, QuestionResponse, RefundSummaryResponse, ReorderResponse, ReturnLineItemResponse, ReturnResponse, ReviewResponse, RfqConvertResponse, RfqQuoteResponse, RfqResponse, RolePermissionsResponse, SearchHistoryEntryResponse, SearchResultResponse, StatementResponse, SuggestResponse, SupportTicketMessageResponse, SupportTicketResponse, TrendingResponse, UserProfileResponse, VendorResponse, WishlistEntryResponse, plus `dto/ApiError`.

### Application service interfaces / impls (64 + 59 files) — full detail in §11, §15
Enumerated by business area in §11 (Application Layer detail tables); every file name appears there grouped under Auth/Cart-Pricing/Catalog-Search/B2B/Approval-Delivery-Order-Payment-Return-Refund.

### Domain model (84 files), ports (91 files), enums (43), exceptions (39) — full detail in §9

### Persistence: entities (58), repositories (57+3), adapters (55), mappers (29) — full detail in §10

### Infra cross-cutting (security 7, gateway 2, external 11, messaging 5, consumer 4, config 20, storage 1, search 4, pdf 2, excel 2, crypto 5, cache 5, seed 2) — full detail in §16, §17, §20

### Test source (245 files) — full detail in §18

---

## 6. Architecture

### 6.1 Layering and dependency direction

```mermaid
flowchart TB
    subgraph API["api — inbound adapter"]
        Ctrl[controllers]
        DTO[DTOs]
        Map1[mappers]
    end
    subgraph APP["application — use-case orchestration"]
        Svc[service interfaces]
        Impl[impl classes]
        Sched[schedulers]
        Evt[events / listeners]
    end
    subgraph DOM["domain — framework-free core"]
        Model[model]
        Port[ports]
        DSvc[domain services]
        Enum[enums]
        Exc[exceptions]
    end
    subgraph INFRA["infra — outbound adapters"]
        Persist[persistence: entity/repo/adapter]
        Sec[security]
        Gw[gateway / external]
        Msg[messaging / consumer]
        Search[search]
        Store[storage]
        Doc[pdf / excel]
        Crypto[crypto]
        Cache[cache]
    end
    Ctrl --> Svc
    Svc -.implemented by.-> Impl
    Impl --> Port
    Impl --> Model
    Sched --> Svc
    Evt --> Impl
    Port -.implemented by.-> Persist
    Port -.implemented by.-> Gw
    Port -.implemented by.-> Msg
    Port -.implemented by.-> Search
    Port -.implemented by.-> Store
    Port -.implemented by.-> Doc
    Port -.implemented by.-> Cache
    Ctrl --> Sec
    Persist --> Crypto
```

Dependency direction is strictly inward: `infra` → `domain` (via port interfaces), never the reverse. `domain/model` contains no JPA/framework imports (verified by inspection). The two exceptions to "every port is implemented in `infra`" are `CartPricingCalculator` and `UserDataExporter`, both implemented in `application/impl` because they are pure orchestration over other ports with no external I/O of their own.

### 6.2 Request lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant RL as RateLimitFilter
    participant Sec as Spring Security<br/>(JwtAuthenticationFilter)
    participant Ctrl as Controller
    participant Svc as application/impl
    participant Port as domain/port
    participant Adp as infra adapter
    participant DB as PostgreSQL

    C->>RL: HTTP request
    RL->>RL: check YAML rule vs Redis counter
    alt over limit
        RL-->>C: 429 RATE_LIMITED (ApiError)
    else under limit
        RL->>Sec: forward
        Sec->>Sec: parse Bearer token (ACCESS, fallback GUEST)
        alt invalid/missing (protected path)
            Sec-->>C: 401/403 (ApiError)
        else valid
            Sec->>Ctrl: AuthenticatedUser principal
            Ctrl->>Svc: delegate (validated DTO)
            Svc->>Port: call repository/gateway port
            Port->>Adp: adapter implementation
            Adp->>DB: SQL
            DB-->>Adp: rows
            Adp-->>Svc: domain model
            Svc-->>Ctrl: result / DomainException
            Ctrl-->>C: mapped response DTO / GlobalExceptionHandler → ApiError
        end
    end
```

### 6.3 Transaction and I/O boundary discipline

The single most consistent architectural pattern in the codebase (repeated across payments, refunds, PO attachments, invoices, and statements) is a **claim → external-I/O → finalize** three-phase shape that deliberately keeps external calls (payment gateway, S3 object storage) *outside* any database transaction:

1. **Claim** (short transaction): a durable row is written/locked recording intent (e.g. `Payment{status=PENDING}`, `PoAttachment{status=PENDING}`).
2. **External I/O** (no transaction, no lock held): the actual gateway call or object-storage write.
3. **Finalize** (short transaction, conditional UPDATE): a claim can only be finalized once; a lost race or a retry after a crash safely converges on the same claim row.

This pattern is documented in code Javadoc as "the refund-gateway discipline" and is deliberately reused for `PoAttachmentServiceImpl`. See §12 (Payment Architecture) and §19 (Concurrency) for the exact mechanics.

### 6.4 Event/outbox architecture (overview; full detail in §15)

Two distinct patterns coexist:
- **In-process domain events** (`application/event` records via `ApplicationEventPublisher`, consumed by `@TransactionalEventListener(phase=AFTER_COMMIT)` listeners in `application/listener`) — used for invoice-row initialization (redundant safety net) and notification triggering.
- **Durable transactional outbox** (`catalog_outbox_events` table, written atomically with the product row by `CatalogWriteServiceImpl`, relayed to RabbitMQ by the `CatalogOutboxRelay` scheduler) — used for catalog→Elasticsearch sync, where losing an in-process event would silently desync search.

### 6.5 Deployment topology

```mermaid
flowchart LR
    subgraph Host["Single JVM process"]
        HTTP[Spring MVC / Tomcat]
        WS[STOMP WebSocket]
        Sched[13 @Scheduled jobs]
        Consumers[RabbitMQ @RabbitListener consumers]
    end
    Client[HTTP/WS clients] --> HTTP
    Client --> WS
    HTTP --> PG[(PostgreSQL 16)]
    Sched --> PG
    HTTP --> Redis[(Redis 7)]
    HTTP --> RMQ[[RabbitMQ 3]]
    Consumers --> RMQ
    Sched --> RMQ
    HTTP --> ES[(Elasticsearch 8.15)]
    HTTP --> S3[(S3-compatible storage<br/>MinIO in dev)]
    HTTP -.-> Google[Google ID token verify]
    HTTP -.-> SMTP[SMTP server]
```

No load balancer, reverse proxy, or multi-instance orchestration is configured in this repository — all of that (along with TLS termination, DNS, secrets injection) is deployment infrastructure the repository assumes but does not provide (see §23).

---

## 7. API Documentation

**Conventions** (verified across all 33 controllers): `@RestController`, Lombok `@RequiredArgsConstructor` constructor injection (no field `@Autowired` anywhere), principal via `@AuthenticationPrincipal AuthenticatedUser`, `@Valid @RequestBody` validation, Swagger-annotated (`@Tag`/`@Operation`/`@SecurityRequirement`). **No controller uses `@PreAuthorize`/`@Secured`** — all HTTP-layer authorization is centralized in `SecurityConfig` (§8); fine-grained B2B/ownership authorization happens inside service `Impl` classes (chiefly `B2bAuthorizerImpl`). **No API versioning scheme** (no `/v1/` prefix). **No global pagination envelope** — ad hoc `page`/`size` query params (default `0`/`20`) returning bare `List<...>`, no total-count/`hasNext` metadata in any response DTO.

### 7.1 Endpoint inventory (33 controllers, 99 HTTP-mapped handler methods)

#### AddressController (`/addresses`)
| Method | Path | Purpose | Auth | Request/Response |
|---|---|---|---|---|
| POST | `/addresses` | Save address | ROLE_USER | `CreateAddressRequest` (type/line1/city/state/zipCode `@NotBlank`) → 201 `AddressResponse` |
| GET | `/addresses` | List my addresses | authenticated | → `List<AddressResponse>` |
| GET | `/addresses/{id}` | Get one | authenticated | → `AddressResponse` |
| DELETE | `/addresses/{id}` | Delete | ROLE_USER | → 204 |

#### ApprovalController (`/approvals`) — B2B approval gate
| Method | Path | Purpose | Auth | Notes |
|---|---|---|---|---|
| GET | `/approvals` | List company's approval requests | authenticated | `APPROVAL_VIEW`, site-scoped; query `companyId`, `page`, `size` |
| GET | `/approvals/{id}` | Detail + action history | authenticated | |
| POST | `/approvals/{id}/approve` | Approve + resume payment | ROLE_USER | `APPROVAL_ACT`; self-approval prohibited |
| POST | `/approvals/{id}/reject` | Reject + cancel order | ROLE_USER | `APPROVAL_ACT` |
| POST | `/approvals/{id}/delegate` | Delegate once | ROLE_USER | `APPROVAL_DELEGATE`; `DelegateApprovalRequest{delegateMemberId}` |

#### ApprovalPolicyController (`/companies/{companyId}/approval-policy`)
GET (COMPANY_VIEW, 404 if unconfigured) / PUT (OWNER-only, version increments) `ApprovalPolicyRequest{amountThreshold, categoryIds, siteIds, roleStages, escalationHours}`.

#### AuthController (`/auth`) — fully public
| Method | Path | Purpose | Notable responses |
|---|---|---|---|
| POST | `/auth/otp/send` | Generate + SMS-dispatch 6-digit OTP; rate-limited 5/hr/phone + cooldown | 400 invalid phone; 429 `OTP_RATE_LIMIT_EXCEEDED` |
| POST | `/auth/otp/verify` | Verify, create account on first login, issue tokens; merges guest cart if guest bearer present | 400 `OTP_EXPIRED`; 401 `OTP_INCORRECT`; 423 `OTP_LOCKED` (3 wrong attempts) |
| POST | `/auth/google` | Server-side Google ID-token verification | 401 invalid token |
| POST | `/auth/guest` | Scoped non-refreshable guest token | — |
| POST | `/auth/refresh` | Rotate refresh token; reuse of stale token revokes the device | 401 `INVALID_REFRESH_TOKEN` |

#### CartController (`/cart`) — GUEST or USER
GET `/cart` (priced cart) · PUT `/cart/items` (`UpsertCartItemRequest{productId,quantity,itemCoupon}`, qty 0 = remove) · DELETE `/cart/items/{productId}` · POST `/cart/coupon` (`ApplyCartCouponRequest{couponCode}`) · DELETE `/cart/coupon` · DELETE `/cart` (clear, 204).

#### CatalogController — public, no prefix/security-requirement
GET `/categories`, `/categories/{id}` (malformed UUID → 404, not 400) · GET `/products` (cursor pagination: `category`,`brand`,`cursor`,`limit` 1–100 default 20) · GET `/products/{id}`.

#### CheckoutController (`/checkout`)
POST `/checkout/intent` (ROLE_USER) — `CreateCheckoutIntentRequest{addressId,slotId,slotDate,expectedTotal,cartId}` → 201 `CheckoutIntentResponse`.

#### CompanyController (`/companies`)
POST `/companies` (ROLE_USER, creator→OWNER) · GET `/companies/{id}` (COMPANY_VIEW) · PATCH `/companies/{id}` (COMPANY_UPDATE) · PATCH `/companies/{id}/status` — HTTP layer allows `hasAnyRole("USER","ADMIN")`, real ADMIN-only enforcement is inside `CompanyServiceImpl.updateStatus` reading `user.roles()` (deliberate: the matcher must not filter-block an ADMIN principal before the service checks).

#### CompanyMemberController (`/companies/{companyId}/members`)
POST (add, MEMBER_MANAGE) · GET (list, MEMBER_VIEW) · PATCH `/{memberId}` (role/sites, MEMBER_MANAGE) · DELETE `/{memberId}` (remove, last-owner protected) · POST `/transfer-ownership` (OWNER-only, old owner demoted to PROCUREMENT_MANAGER).

#### CompanyRolePermissionController (`/companies/{companyId}/role-permissions`)
GET (effective per-role sets) · PUT `/{role}` (replace non-OWNER role's permissions; OWNER immutable).

#### CompanySiteController (`/companies/{companyId}/sites`)
POST (SITE_MANAGE) · GET (SITE_VIEW) · PATCH `/{siteId}` (deactivation blocked while active orders reference it).

#### DeliverySlotController (`/delivery-slots`)
GET (authenticated; query `date`, defaults today).

#### DeviceController (`/users/me`)
GET `/devices` · DELETE `/devices/{id}` (204; 404 if not owned) · POST `/logout-all-devices` (kill all sessions, reissue current).

#### LoginHistoryController (`/users/me/login-history`)
GET, paginated (IP + device fingerprint audit log).

#### NotifyMeController
POST `/products/{id}/notify-me` — idempotent back-in-stock subscription (ROLE_USER).

#### order/InvoiceController
GET `/orders/{id}/invoice` — signed URL + status (ROLE_USER, explicit rule).

#### order/OrderController (`/orders`)
| Method | Path | Purpose | Auth | Notes |
|---|---|---|---|---|
| POST | `/orders` | Create order + initiate payment | ROLE_USER | **Requires `Idempotency-Key` header**; `CreateOrderRequest{addressId,slotId,slotDate,expectedTotal,cartId,siteId}` → 201 |
| POST | `/orders/{id}/payments/retry` | Retry on PAYMENT_PENDING | ROLE_USER | |
| GET | `/orders` | List mine, paginated | ROLE_USER | |
| GET | `/orders/{id}` | Detail | ROLE_USER | |
| POST | `/orders/{id}/reorder` | Add items to new cart | ROLE_USER | B2B rejected |
| GET | `/orders/{id}/tracking` | Tracking | ROLE_USER | |
| PUT | `/orders/{id}/status` | **Delivery-partner webhook** | **public** at HTTP layer | `X-API-Key` checked inside `OrderTrackingService` (application-level, not Spring Security) |
| POST | `/orders/{id}/reschedule` | Within modification window | ROLE_USER | |
| POST | `/orders/{id}/cancel` | Within modification window | ROLE_USER | |
| POST | `/orders/{id}/call-driver` | Masked call proxy | ROLE_USER | |

#### order/PaymentWebhookController (`/api/webhooks/payment`) — public
POST — `PaymentWebhookRequest{orderId,status,signature}` → 200. Authentication is HMAC signature verification inside `PaymentWebhookServiceImpl`, not Spring Security.

#### order/RefundWebhookController (`/api/webhooks/refund`) — public
POST — `RefundWebhookRequest{returnId,gatewayRefundId,status,signature}`, same HMAC pattern via `RefundWebhookServiceImpl`.

#### order/ReturnController — multipart
POST `/orders/{id}/return` (ROLE_USER, multipart: `request` part + `photos` part) · GET `/returns/{id}` (USER/VENDOR/ADMIN) · POST `/returns/{id}/approve|schedule-pickup|pickup|reject|qc-pass` (VENDOR/ADMIN only).

#### PoAttachmentController — multipart
POST `/orders/{orderId}/po` (upload, PO_UPLOAD) · POST `/orders/{orderId}/po/{attachmentId}/retry` (finalize race: winner 201, loser 200).

#### PoImportController (`/po`)
POST `/po/bulk` (multipart, **requires `Idempotency-Key`** + query `companyId`, PO_UPLOAD; replay → 200, fresh → 201) · GET `/po/imports/{id}` (PO_VIEW) · POST `/po/imports/{id}/convert` (PO_CONVERT → B2B draft cart).

#### QnaController
GET `/products/{id}/questions` (public) · POST (ROLE_USER) → 201 · POST `/questions/{id}/answers` (USER/VENDOR/ADMIN; `source` resolved server-side from JWT roles, never client-supplied).

#### ReviewController
GET `/products/{id}/reviews` (public) · POST (ROLE_USER; `SubmitReviewRequest{rating 1-5, comment ≤2000}`) → 201.

#### RfqController (`/rfq`)
POST (RFQ_CREATE) → 201 · GET `/{id}` (RFQ_VIEW) · GET `/{id}/quotes` (QUOTE_VIEW, sorted ascending) · POST `/{id}/cancel` (RFQ_CANCEL, OPEN only) · POST `/{id}/convert` (RFQ_CONVERT; `RfqConvertRequest{quoteId}`).

#### RfqQuoteAdminController (`/admin/rfqs`) — ROLE_ADMIN only
POST `/{rfqId}/quotes` — `AdminQuoteSubmitRequest{vendorId,totalAmount,validUntil}` → 201.

#### SearchController — mostly public
GET `/search` (fuzzy + Hindi/English alias + category filter, works anonymous or authenticated) · GET `/search/suggest` (Redis-cached 60s) · GET `/search/trending` (Redis-cached 10min) · POST `/search/image` (multipart) — **not** covered by the GET-scoped public rule, falls to `POST /** → ROLE_USER`; currently a stub returning zero matches.

#### SearchHistoryController (`/users/me/search-history`)
GET (last 20) · DELETE (clear, 204) — ROLE_USER.

#### StatementController
GET `/companies/{companyId}/statements` (STATEMENT_VIEW, latest version per period) · GET `/statements/{id}` (historical READY versions stay accessible).

#### SupportChatController
POST `/support/chat` (ROLE_USER) — stub always escalates to a human ticket.

#### SupportTicketController
POST `/support/tickets` (create + first message atomically) · GET (list mine, paginated) · GET `/{id}` (own-only for customers — enforced in service, not HTTP; any for VENDOR/ADMIN) · POST `/{id}/messages` (append) · POST `/{id}/escalate` (VENDOR/ADMIN only).

#### UserController (`/users`)
GET `/me` · PUT `/me` (null fields unchanged; setting GST marks `gstinStatus=PENDING`, format-only validation) · GET `/me/export` (DPDP export, synchronous JSON, every section always present even if empty) · POST `/me/delete-request` (DPDP deletion; 202, 409 if already pending).

#### VendorAdminController (`/admin/vendors`) — ROLE_ADMIN only
POST / GET / PATCH `{vendorId}`.

#### WishlistController
GET `/users/me/wishlist` · POST (idempotent add) → 201 · DELETE `/{productId}`.

### 7.2 Global error handling

Class: `api.GlobalExceptionHandler` (`@RestControllerAdvice`). Response envelope `ApiError{timestamp, status, error, code, message, path}` (`api/dto/ApiError.java`) — the **same envelope** is reused by `SecurityConfig`'s 401/403 handlers and `RateLimitFilter`'s 429 renderer, giving the whole API one error shape.

`STATUS_BY_EXCEPTION` is a static map walked up the `DomainException` superclass chain (subclasses inherit their parent's mapped status; unmapped subclasses default to 500):

| Exception | Status | Exception | Status |
|---|---|---|---|
| `BadRequestException` | 400 | `RfqValidationException` | 422 |
| `UnauthorizedException` | 401 | `QuoteValidationException` | 422 |
| `ForbiddenException` | 403 | `VendorNotRoutableException` | 422 |
| `NotFoundException` | 404 | `InvalidPoStateException` | 409 |
| `LockedException` | 423 | `PoAttachmentExistsException` | 409 |
| `TooManyRequestsException` | 429 | `PoUploadInProgressException` | 409 |
| `SlotUnavailableException` | 409 | `PoImportValidationException` | 400 |
| `CheckoutValidationException` | 422 | `InvalidApprovalStateException` | 409 |
| `ContractPriceOverlapException` | 409 | `ApprovalPolicyValidationException` | 400 |
| `ProductNotPricedException` | 422 | `PaymentGatewayException` | 502 (message generalized — raw gateway error never leaked) |
| `GstRateUnresolvedException` | 422 | `MemberAlreadyExistsException` | 409 |
| `InvalidOrderStateException` | 409 | `SiteInUseException` | 409 |
| `InvalidReturnStateException` | 409 | `SiteNameTakenException` | 409 |
| `ReturnAlreadyExistsException` | 409 | `LastOwnerProtectedException` | 422 |
| `DeleteRequestPendingException` | 409 | `OwnerPermissionsImmutableException` | 422 |
| `InvalidSupportTicketStateException` | 409 | `PermissionEscalationGuardException` | 422 |
| `ModificationWindowExpiredException` | 409 | `InvalidRfqStateException` | 409 |
| `PaymentRetryInProgressException` | 409 | `DuplicateQuoteException` | 409 |

Framework-level handlers (never fall through to generic 500): `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` (joined `field: message` list); `IllegalArgumentException` → 400 `INVALID_REQUEST`; one handler covers `MissingRequestHeaderException`/`MissingServletRequestParameterException`/`HttpMessageNotReadableException`/`MethodArgumentTypeMismatchException`/`HttpRequestMethodNotSupportedException`/`MaxUploadSizeExceededException`/`HandlerMethodValidationException` (405/413/400 as appropriate); catch-all `Exception` → 500 `INTERNAL_ERROR` (full stack trace logged server-side, generic message to client).

### 7.3 Upload / body size limits

`spring.servlet.multipart.max-file-size: 2MB`, `max-request-size: 3MB` (same in prod and test config) — governs PO XLSX uploads and return-photo uploads. Exceeding either throws `MaxUploadSizeExceededException` → 413 `PAYLOAD_TOO_LARGE`. No per-endpoint override found. Verified by test class `PayloadValidationSizeLimitsTest`.


## 8. Authentication and Authorization

### 8.1 Authentication mechanisms

There is **no password-based login** in this system (no `PasswordEncoder`/BCrypt found anywhere). Three ways to obtain a token, all via `AuthController` → `AuthenticationFacade` → `application/impl/AuthServiceImpl`:

1. **OTP (phone)**: `POST /auth/otp/send` → `OtpSendService.send(phone)` (rate-limited: `RedisOtpRateLimiter` enforces a send cooldown, hourly send cap, and failed-verification lockout — see §8.6) → `POST /auth/otp/verify` → `OtpVerificationService.verify(phone, otp)` (throws on expired/wrong/3-strikes-locked) → `UserAccountService.findOrCreateByPhone(phone)` → guest-cart merge if a guest bearer token was presented → `LoginEventRecorder.record(...)` → session issuance.
2. **Google Sign-In**: `POST /auth/google` → `GoogleIdentityGateway.verify(idToken)` (real integration: `infra/external/GoogleTokenVerifier`, uses `com.google.api.client.GoogleIdTokenVerifier` against Google's public keys, audience-restricted to `google.client-id`, always re-verified server-side, no trust caching) → `UserAccountService.findOrCreateByGoogle(...)`.
3. **Guest session**: `POST /auth/guest` → creates a guest `User`, issues only a `GUEST` token (no refresh token) — scoped to browsing/cart endpoints only.

**Session issuance** (`AuthServiceImpl.issueSession`): mints `deviceId = UUID.randomUUID()`, calls `TokenIssuer.issueAccessToken(userId, deviceId, ["USER"], companyMembershipResolver.resolveByUserId(userId))` — B2B company memberships are embedded as JWT claims **at issuance time** — and `issueRefreshToken()`, then registers the device via `DeviceRegistry.create(...)`.

**Refresh**: `POST /auth/refresh` → `AuthServiceImpl.refresh()` validates the presented token as `TokenType.REFRESH`, calls `RefreshTokenRotator.validateForRefresh(deviceId, userId, refreshToken)` — **reuse of an already-rotated refresh token is treated as compromise and revokes the device** — re-resolves B2B memberships fresh (not from the old token), rotates the stored refresh-token hash.

### 8.2 JWT issuance/validation (`infra/security`)

- `JwtCodec` (package-private): sole holder of the HS256 signing key (`Keys.hmacShaKeyFor(secret bytes)`, via `io.jsonwebtoken`/JJWT 0.12.6). Verifies signature + expiry only; `typ` claim checked separately by callers.
- `JwtTokenIssuer`: three `TokenType`s — `ACCESS` (`typ`, `roles`, `deviceId`, `b2b`: array of `{cid, role, sites}`), `REFRESH` (`typ`, `deviceId`), `GUEST` (`typ`, `roles=["GUEST"]`, `sessionId`). TTLs from `JwtProperties`.
- `JwtTokenValidator`: decodes claims; **fail-safe parses** the optional `b2b` claim — any malformed entry (bad UUID, unknown role, non-list sites) is silently dropped rather than rejecting the whole token, since the JWT's B2B claim only ever *narrows* what a caller can attempt (money-path operations independently re-verify against the database).
- `JwtAuthenticationFilter` (`OncePerRequestFilter`, inserted before `UsernamePasswordAuthenticationFilter`): tries `ACCESS`, falls back to `GUEST`. For `GUEST` tokens, an additional per-request DB lookup (`guestIdentityStillActive()`) rejects tokens whose guest identity has since been merged into a real account (`mergedIntoUserId != null`) — a merged guest's token dies globally, not just on cart endpoints. Explicitly marked in code as a deliberate simplification (`// ponytail:` comment) — per-row DB lookup rather than a Redis-cached denylist, "if guest traffic ever gets hot." Spring `GrantedAuthority`s are derived **only** from `roles` (`ROLE_<role>`) — B2B company roles never become Spring authorities; they ride on the `AuthenticatedUser` principal for service-level checks only. Any `RuntimeException` during validation is swallowed (request proceeds unauthenticated; `SecurityConfig` path rules then decide the outcome).

### 8.3 Claims and principal

`common/AuthenticatedUser` (record): `userId`, `deviceId`, `roles: List<String>`, `b2bMemberships: List<B2bMembership>` (defensively copied). `b2bMembership(companyId)` looks up the caller's role in a specific company. Explicitly documented: B2B roles here are "ordinary-check credentials only" — every money-path operation independently re-verifies membership against the database rather than trusting this JWT-derived snapshot.

### 8.4 HTTP-layer authorization (`infra/config/SecurityConfig`)

Single `SecurityFilterChain` bean. CSRF disabled (stateless bearer-token API), `SessionCreationPolicy.STATELESS`.

- **Public** (`permitAll`): `/auth/**`, `/api/webhooks/**` (payment/refund webhooks — HMAC-verified inside the service layer instead), `/ws/**` (STOMP handshake — see §8.5), `/actuator/health`, `GET /categories/**` + `GET /products/**`, `GET /search/**`, `PUT /orders/*/status` (delivery-partner webhook, gated by application-level `X-API-Key` instead).
- **Swagger/OpenAPI gating**: `springdoc.swagger-ui.enabled` (default `true` if unset in code, but `false` in the base `application.yaml` — see §17) toggles `/v3/api-docs/**`,`/swagger-ui/**`,`/swagger-ui.html` between `permitAll` and `hasRole("ADMIN")` — Swagger degrades to admin-only rather than ever 404ing.
- **`/admin/**` → `hasRole("ADMIN")`** exclusively — the application ADMIN role and B2B company roles are deliberately disjoint authority systems (an ADMIN gets no `/rfq/**` access without a separate company membership).
- **`PATCH /companies/*/status` → `hasAnyRole("USER","ADMIN")`** — HTTP layer intentionally permissive so a USER-authenticated-but-not-yet-service-checked ADMIN principal reaches the service, which does the real enforcement.
- **`/users/**` → `hasRole("USER")`**; **`/cart/**` → `hasAnyRole("GUEST","USER")`**.
- Fine-grained per-path/method rules for support tickets, returns (mutation endpoints VENDOR/ADMIN only), Q&A answers (USER/VENDOR/ADMIN, source resolved server-side).
- **Guest-token ceiling**: blanket `POST|PUT|PATCH|DELETE /** → hasRole("USER")` after all specific rules — a guest token can browse (GET) broadly but write nowhere outside the explicitly-listed guest-scoped endpoints.
- **`anyRequest().authenticated()`** final fallback.
- Custom `authenticationEntryPoint` (401 `UNAUTHENTICATED`) / `accessDeniedHandler` (403 `ACCESS_DENIED`), both rendering `ApiError` — bypassing Spring's default whitelabel error pages.
- **Filter order**: `RateLimitFilter` is `@Order(HIGHEST_PRECEDENCE)`, a plain servlet filter running *ahead of* the entire Spring Security chain (rejects before any JWT parsing happens); `JwtAuthenticationFilter` sits before `UsernamePasswordAuthenticationFilter` within the chain.

### 8.5 WebSocket authentication

`WebSocketAuthChannelInterceptor` (`infra/security`): authenticates STOMP `CONNECT` frames (Bearer / `token` / `access_token` native headers, ACCESS-type tokens only) and authorizes STOMP `SUBSCRIBE` to `/topic/orders/{id}` by checking the subscribing user owns that order (`OrderRepository.findOrderOwnerId`), throwing `ForbiddenException`/`UnauthorizedException` otherwise. `WebSocketConfig`: `/ws` endpoint, all origins allowed (`*`), simple broker on `/topic`, app-destination prefix `/app`.

### 8.6 Rate limiting

`RateLimitFilter` (`infra/security`, `@Order(HIGHEST_PRECEDENCE)`) — 100% YAML-driven via `RateLimitProperties` (`security.rate-limit.rules.<name>: {path, method, limit, window}`), keyed by client IP (`ClientIpResolver` — leftmost `X-Forwarded-For` entry, else `getRemoteAddr()`, shared identically with OTP/login-audit code so the two never disagree). `OPTIONS` exempt (CORS preflight never burns budget). Backed by `RedisRateLimiter` via a Lua script (`FixedWindowCounter`) that atomically does `INCR` then `PEXPIRE` only on first creation — closing the crash window where a counter key could exist without a TTL and rate-lock a subject forever. On violation: 429 `ApiError{code=RATE_LIMITED}` rendered directly by the filter (bypasses `GlobalExceptionHandler`, since filters run before `DispatcherServlet`). Default rule set (application.yaml): `search` 30/1m, `google` POST 10/1m, `review-create`/`question-create`/`answer-create`/`ticket-create`/`return-create` POST 10/1m each.

Separately, `RedisOtpRateLimiter` enforces three independent phone-keyed guards: send cooldown (`otp.send-cooldown-seconds`, default 60), hourly send cap (`otp.rate-limit-per-hour`, default 5), and failed-verification lockout (`otp.max-attempts`, default 3, TTL = `otp.ttl-seconds`) — throwing `TooManyRequestsException`/`LockedException` respectively.

### 8.7 B2B company authorization — the single choke point

`application/impl/B2bAuthorizerImpl.authorize(userId, companyId, permission, resourceSiteId, critical)`:
1. If `critical=true`: locks the `Company` row (`findByIdForUpdate`) and rejects with `ForbiddenException("COMPANY_SUSPENDED")` if `status != ACTIVE`. **This is the only place company suspension is enforced** — every B2B mutation routes through here, so a suspended company becomes fully inert in one code path.
2. Resolves `CompanyMember` for `(companyId, userId)`; not found → `NotFoundException` (existence-hiding — a non-member gets the same 404 as a truly-nonexistent company).
3. `OWNER` has implicit all-permissions (never stored). Any other role: looked up via `CompanyRolePermissionRepository.findPermissions(companyId, role)`, membership in that set required.
4. If `resourceSiteId` given: `CompanySiteAssignmentRepository.findSiteIdsByMemberId()` — an **empty** list means unscoped (all sites); non-covering site on a `critical` call → `ForbiddenException("SITE_OUT_OF_SCOPE")`; on a non-critical (read) call → `NotFoundException` (existence-hiding, consistent with reads elsewhere).

Every critical B2B mutation locks its own aggregate row *before* calling the authorizer, establishing one global lock order per B2B service (documented per-class, e.g. `RfqServiceImpl`: RFQ row → companies; `CompanyMembershipServiceImpl`: company row → member rows `ORDER BY id`).

The 22-permission vocabulary (`domain/enums/CompanyPermission`) includes `ORDER_CREATE`, `ORDER_VIEW`, `RFQ_CREATE`, `RFQ_VIEW`, `RFQ_CANCEL`, `RFQ_CONVERT`, `QUOTE_VIEW`, `PO_UPLOAD`, `PO_VIEW`, `PO_CONVERT`, `APPROVAL_VIEW`, `APPROVAL_ACT`, `APPROVAL_DELEGATE`, `COMPANY_VIEW`, `COMPANY_UPDATE`, `MEMBER_VIEW`, `MEMBER_MANAGE`, `SITE_VIEW`, `SITE_MANAGE`, `ROLE_PERMISSION_MANAGE`, `STATEMENT_VIEW`, and one more (defaults for PROCUREMENT_MANAGER/SITE_SUPERVISOR/ACCOUNTANT/VIEWER are hardcoded in `domain/service/CompanyPermissionDefaults`, consulted only at company-creation time; thereafter `company_role_permissions` rows are sole source of truth). `PermissionEscalationGuardException` prevents granting `ROLE_PERMISSION_MANAGE` to a non-OWNER role (the DB `CHECK` on `company_role_permissions.role` also excludes `OWNER` entirely, making an OWNER-permission row structurally unrepresentable).

### 8.8 Owner / admin / vendor boundaries

- **Application ADMIN** (`ROLE_ADMIN`, a Spring authority) and **B2B company roles** (`CompanyRole`, JWT-carried but never a Spring authority) are entirely separate authority systems — an ADMIN has zero implicit B2B access.
- **VENDOR** (`ROLE_VENDOR`): gates return-lifecycle mutations and support-ticket escalation alongside ADMIN. Vendors (the `Vendor` domain model, B2B RFQ routing target) have **no user/authentication relationship at all** ("vendors never log in" — quotes are submitted on their behalf via `RfqQuoteAdminController`, ADMIN-only).
- **OWNER** (a `CompanyRole` value): implicit all-permissions, cannot be demoted below "at least one OWNER remains" (`LastOwnerProtectedException`, enforced under row lock in `CompanyMembershipServiceImpl`), permissions immutable (`OwnerPermissionsImmutableException`).

### 8.9 Webhook authentication (payment/refund/delivery)

Payment and refund webhooks: fail-closed **HMAC-SHA256** signature verification (`MessageDigest.isEqual` constant-time compare) — missing/blank secret or signature → `UnauthorizedException`, unconditionally, no dev bypass. `PaymentProperties.webhookSecret` / equivalent has **no default** and fails Spring context startup if blank. Delivery-partner webhook (`PUT /orders/*/status`): `X-API-Key` header checked against `DeliveryProperties.webhookApiKey` inside `OrderTrackingService` — this key has an **empty-string soft default** (not fail-closed like the others), flagged in §20/§28 as a residual hardening gap.

### 8.10 Fail-closed configuration summary

`JWT_SECRET`, `PII_MASTER_KEY`, `PAYMENT_WEBHOOK_SECRET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` all have **no default** in `application.yaml` — Spring refuses to start without them. `DB_PASSWORD`, `RABBITMQ_PASSWORD`, `DELIVERY_WEBHOOK_API_KEY` have soft (non-empty or empty-string) defaults and do **not** fail closed — a genuine, if minor, inconsistency in an otherwise deliberately fail-closed secret-handling posture.

---

## 9. Domain Model

The full 84-class catalogue, grouped by aggregate, is reproduced below (verified field lists — see §4 for the package-level framing). All relationships are bare `UUID` id references; there are no JPA associations at this layer (`domain/model` imports no `jakarta.persistence.*`, confirmed by inspection).

### 9.1 Identity / Auth
`User` (POJO: id, phone, email, googleId, name, businessName, gstNumber, isGuest, mergedIntoUserId, gstinStatus, createdAt, updatedAt), `Device`, `AuthSession`, `IssuedToken`, `TokenClaims` (userId, deviceId, roles, b2bMemberships — `deviceId==null` signals a guest token), `B2bMembership` (companyId, role, siteIds — empty siteIds = unscoped), `GoogleUserInfo`, `LoginEvent`, `OtpSendResult`, `IdempotencyKey`.

### 9.2 Address / Geo
`Address` (id, userId, type: free-text not enum, line1/2, city, state, zipCode, lat/lng, isServiceable).

### 9.3 Catalog
`Category` (self-referencing `parentId`, `attributeSchema: List<CategoryAttribute>`, `returnWindowDays` default 7), `CategoryAttribute`, `Product` (attributes/images/stock as structured fields, `hsnCode` — a soft cross-table reference to `hsn_gst_rates`, not a DB FK), `ProductImage`, `ProductDetail` (read-model), `ProductPage`/`ProductPageCursor` (keyset pagination, cursor = base64 `createdAt|id`), `ProductSearchHit`, `ProductSyncPayload` (the Elasticsearch-ready projection built once at write time), `StockEntry` (explicitly a stub, static/seeded only), `BulkPricingTier`, `HsnGstRate` (PK = HSN code string), `MarginRule`, `Review` (`verifiedPurchase` always false — stubbed), `Question`/`Answer`/`QuestionThread`, `WishlistEntry`, `NotifyMeSubscription`, `SearchQueryLogEntry`/`TrendingQueryCount`, `CatalogOutboxEvent`.

### 9.4 Pricing
`ContractPrice`/`CompanyContractPrice` (user- vs company-level negotiated pricing windows, overlap rejected by GiST exclusion), `PricingRequest`, `PriceCalculationResult` (~20 fields spanning base→tier→contract→coupon→margin-floor→GST→final), `ResolvedContract`, `PricedCart`/`PricedCartLineItem`, `Coupon`/`CouponRedemption`.

### 9.5 Cart / Checkout
`Cart` (id, userId, projectId, type: `CartType`, appliedCartCoupon, items, companyId — null for B2C), `CartLineItem` (unitPriceOverride for negotiated/RFQ-converted pricing), `CheckoutIntent`.

### 9.6 Orders
`Order` — the largest aggregate; full state machine in §9.9. `OrderLineItem` (lineTotal is the GST-inclusive charged total; unitPrice = total/qty rounded, so `unitPrice×quantity` can drift by paise — an explicit documented rounding disclosure). `OrderTracking`/`DeliveryTrackingEvent`. `OrderInvoiceSnapshot` (+ `InvoiceLineItemSnapshot`) — the frozen snapshot fed to the PDF renderer.

### 9.7 Delivery Slots
`DeliverySlotOption`, `SlotConfiguration` (recurring template), `DeliverySlotCounter` (capacity, currentCount; `hasCapacity()`/`increment()`/`decrement()` floor-clamped at 0), `DeliverySlotLock` (`isActive(now)` = ACTIVE and not expired).

### 9.8 Payments / Refunds / Returns / GST
`Payment` (markSuccess/markFailed — **unguarded**, see §9.9), `PaymentReference`, `PaymentReconciliation` (manual-recovery record for ambiguous gateway outcomes), `Refund` (markSuccess/markFailed, unguarded), `RefundReference`, `Return` (full state machine, §9.9), `ReturnLineItem`, `GstNote` (CREDIT/DEBIT, 1:1 per type per return), `Invoice` (claim/markReady/markDlqRetry/markPending/markFailed).

### 9.9 State Machines

**`OrderStatus`** (7 values: `PAYMENT_PENDING`, `PENDING_APPROVAL` — B2B gate only, `CONFIRMED`, `PACKED`, `DISPATCHED`, `DELIVERED`, `CANCELLED`). Every transition is a guarded method **on the `Order` record itself**, throwing `InvalidOrderStateException(from, target)` on violation:
```mermaid
stateDiagram-v2
    [*] --> PAYMENT_PENDING: create() (ungated)
    [*] --> PENDING_APPROVAL: create() (B2B gated)
    PENDING_APPROVAL --> PAYMENT_PENDING: resumePayment()
    PENDING_APPROVAL --> CANCELLED: cancelPendingApproval()
    PAYMENT_PENDING --> CONFIRMED: confirm()
    PAYMENT_PENDING --> CANCELLED: cancel()
    CONFIRMED --> PACKED: pack()
    CONFIRMED --> CANCELLED: cancelConfirmed() / cancelFromDelivery()
    PACKED --> DISPATCHED: dispatch()
    PACKED --> CANCELLED: cancelFromDelivery()
    DISPATCHED --> DELIVERED: deliver()
    DISPATCHED --> CANCELLED: cancelFromDelivery()
```
`reschedule()` and `updateDriver()` mutate fields without changing status.

**`ReturnStatus`** (8 values): linear happy path `REQUESTED → APPROVED → PICKUP_SCHEDULED → PICKED_UP → QC → REFUND_INITIATED → REFUND_COMPLETED`, each transition guarded on `Return` itself (`InvalidReturnStateException`). `reject()` is a side-exit from `REQUESTED`/`APPROVED`/`PICKUP_SCHEDULED`/`PICKED_UP` only (never from `QC` or later) → `REJECTED`.

**`PaymentStatus`/`RefundStatus`** (PENDING/SUCCESS/FAILED each): **no guard at the domain-record level** — `Payment.markSuccess()`/`markFailed()` and `Refund.markSuccess()`/`markFailed()` can overwrite any prior status. Idempotency/already-terminal guarding is an **application-layer responsibility** (payment/refund webhook services), not enforced by the domain model itself — a notable, explicitly-flagged domain-model gap (see §19, §28).

**`DeliverySlotLockStatus`** (ACTIVE/RELEASED/CONSUMED/EXPIRED): transitions performed via `DeliverySlotLockRepository.tryTransitionStatus(id, from, to)` — a repository-level CAS, not a record method (the record is read-only regarding status).

**`InvoiceStatus`** (PENDING/GENERATING/READY/DLQ_RETRY/FAILED): transition methods on `Invoice`, none guard their *from* state at the record level (unlike Order/Return) — callers (invoice scheduler/DLQ worker) are responsible for calling them appropriately.

**`StatementStatus`** (PENDING/GENERATING/READY/DLQ_RETRY) + **`StatementEmailStatus`** (NONE/SENT/FAILED/SKIPPED): two independent dimensions on one `Statement` record, mirroring `InvoiceStatus` by design.

**`RfqStatus`** (OPEN/EXPIRED/CONVERTED/CANCELLED): only `OPEN` admits quote submission/cancel/convert; others terminal. Guarded at the application layer under row lock, or by the expiry sweeper's conditional bulk UPDATE — no guard methods on the `Rfq` record itself.

**`RfqQuoteStatus`** (1 value: `SUBMITTED` only) — deliberately no withdrawal/revision/acceptance lifecycle (a locked design decision, "OQ-5"); selection-worthiness is derived at read time from `validUntil`/expiry.

**`ApprovalRequestStatus`** (PENDING/APPROVED/REJECTED/CANCELLED) on `ApprovalRequest`: `approve()`/`reject()`/`cancel()` route through a private `transition()` requiring PENDING (`InvalidApprovalStateException.notPending`). `assign()` (single-hop delegation, no re-delegation) and `escalateTo()` (validates stage progression, clears delegation, resets due-clock) are also PENDING-gated. `PENDING` may persist indefinitely — no timeout/auto-cancel; escalation only moves stage/approver, never auto-decides.

**`CompanyStatus`** (ACTIVE/SUSPENDED): `suspend()`/`activate()` unguarded at the record level; enforcement is entirely in `B2bAuthorizerImpl` (§8.7).

**`PoImportStatus`** (RECEIVED/PARSED/REVIEW/CONVERTED/FAILED_STRUCTURE): RECEIVED/PARSED are transient in-pipeline states; REVIEW/CONVERTED/FAILED_STRUCTURE are the persisted end states.

**`PoAttachmentStatus`** (PENDING/STORED): the durable-claim two-phase pattern (§6.3).

**`SupportTicketStatus`** (OPEN/ESCALATED/RESOLVED/CLOSED) — **a named, deliberate permanent gap**: only one transition (`escalate()`, OPEN-only) is exposed in this phase, so every escalated ticket (including every chat-created ticket, since `/support/chat` always escalates) stays `ESCALATED` forever. RESOLVED/CLOSED are locked enum values with no reachable transition — explicitly documented in the enum's own Javadoc as intentional, not an oversight.

**`DeleteRequestStatus`** (PENDING/PROCESSED): `markProcessed()`, `isDue()`.

---

## 10. Database

### 10.1 Configuration

- `spring.datasource.url`: `jdbc:postgresql://localhost:5432/builddash` (env `DB_HOST`/`DB_PORT`/`DB_NAME`), user/password default to `builddash`/`builddash` (soft default, **not fail-closed** — see §8.10).
- HikariCP: `maximum-pool-size=20`, `minimum-idle=10`, `connection-timeout=20000ms`, `idle-timeout=300000ms`, `max-lifetime=1800000ms` (pool size env-overridable; timeouts fixed).
- `spring.jpa.hibernate.ddl-auto: validate` — schema changes must go through Flyway; Hibernate only validates the mapping matches.
- `spring.jpa.open-in-view: false` — deliberately disabled (avoids lazy-loading during response serialization, forces explicit fetch planning in services).
- `spring.flyway.enabled: true`, `spring.flyway.locations: classpath:db/migration/{vendor}` (on-disk: `src/main/resources/db/migration/postgresql/`).
- Test profile: same Flyway migrations run against a real Testcontainers Postgres (`postgres:16-alpine`) — no H2 mirror, deliberately, to avoid dialect drift.

### 10.2 Complete Flyway Migration Inventory (41 migrations, in order)

| Version | Filename | Purpose | Key schema objects |
|---|---|---|---|
| V1 | init_schema.sql | Foundational auth/user schema | `users` (phone/email/google_id partial-unique, GSTIN CHECK), `devices`, `login_events`, `hsn_gst_rates`; `pgcrypto` ext |
| V2 | seed_hsn_gst_rates.sql | Seed data | 12 HSN/GST rows for construction materials |
| V3 | catalog_schema.sql | Catalog foundation | `categories` (self-FK, JSONB attribute_schema), `products` (JSONB attrs/images/stock, status CHECK) |
| V4 | review_qna_wishlist_backinstock.sql | Engagement | `reviews`, `questions`, `answers`, `wishlist_entries`, `notify_me_subscriptions` |
| V5 | catalog_outbox_events.sql | Outbox pattern | `catalog_outbox_events` (status CHECK PENDING/PUBLISHED/PROCESSED) |
| V6 | search_queries.sql | Search analytics | `search_queries` |
| V7 | pricing_schema.sql | Pricing engine | `product_base_prices`, `bulk_pricing_tiers`, `contract_pricing` (+ `btree_gist`, GiST exclusion), `coupons` (case-insensitive unique), `coupon_redemptions`, `margin_rules` (XOR scope CHECK) |
| V8 | create_addresses_table.sql | Addresses | `addresses` |
| V9 | create_delivery_slots_tables.sql | Delivery slots | `slot_configurations` (4 seeded rows), `delivery_slot_counters` (capacity CHECK), `delivery_slot_locks` |
| V10 | create_cart_tables.sql | Cart | `carts` (unique user+project via COALESCE), `cart_line_items` (unique cart+product); `coupons.min_order_value` |
| V11 | create_order_tables.sql | Orders/payments | `orders`, `order_line_items`, `payments`, `idempotency_keys` |
| V12 | add_lock_id_to_orders.sql | Backfill | `orders.delivery_slot_lock_id` NOT NULL (backfilled) |
| V13 | add_delivery_tracking_and_order_fields.sql | Tracking | `delivery_tracking_events`; `orders.driver_id/driver_phone`; `carts.cart_type` |
| V14 | update_cart_unique_index.sql | Index rework | Cart uniqueness now includes `cart_type` |
| V15 | hot_path_indexes_and_line_totals.sql | Perf + precision | `idx_orders_user_id`, `idx_products_status_created_id`; `order_line_items.line_total` (GST-inclusive) |
| V16 | guest_users_and_cart_merge.sql | Guest checkout | `users.is_guest`, `users.merged_into_user_id` |
| V17 | create_return_tables.sql | Returns | `returns`, `return_line_items`; `categories.return_window_days` |
| V18 | create_refunds_table.sql | Refunds | `refunds` (unique `gateway_refund_id`) |
| V19 | create_gst_and_invoice_tables.sql | GST/invoicing | `gst_sequences` (seeded FY 2026-2027), `invoices` (unique number, unique order_id), `gst_notes` (unique number) |
| V20 | create_notification_logs.sql | Notifications | `notification_logs` (polymorphic reference_id) |
| V21 | create_support_tables.sql | Support | `support_tickets` (SLA partial index), `support_ticket_messages` |
| V22 | pii_blind_indexes.sql | PII groundwork | Widens PII cols to TEXT; adds `phone_idx`/`email_idx`/`google_id_idx` blind-index cols + partial unique indexes |
| V23 | delete_requests.sql | DPDP deletion | `delete_requests` (partial unique PENDING-per-user) |
| V24 | one_active_return_per_order.sql | H1 hardening | `uq_returns_one_active_per_order` partial unique (excludes REJECTED) |
| V25 | company_foundation.sql | B2B foundation | `companies`, `company_sites`, `company_members`, `company_site_assignments`, `company_contract_pricing` (+ GiST exclusion); B2B cols on orders/carts |
| V26 | b2b_role_permissions.sql | B2B RBAC | `company_role_permissions` (composite PK, CHECK vocabulary); default-permission backfill |
| V27 | rfq_vendor_tables.sql | RFQ/vendor | `vendors`, `vendor_categories`, `rfqs`, `rfq_items`, `rfq_quotes` (unique rfq+vendor), `rfq_routes` |
| V28 | po_import_tables.sql | PO import | `po_attachments` (unique order_id), `po_imports` (unique company+idempotency_key), `po_import_rows` |
| V29 | approval_gate.sql | B2B approvals | `company_approval_policies`, `approval_requests` (immutable snapshot cols), `approval_actions` (unique request+type+stage); `orders.delivery_slot_lock_id` now nullable |
| V30 | monthly_statements.sql | Statements | `statements` (dual unique constraints), `statement_sequences` |
| V31 | gst_notes_return_type_unique.sql | H1.1 backstop | `uq_gst_notes_return_type` |
| V32 | carts_consumed_at.sql | H2.1 | `carts.consumed_at` |
| V33 | pricing_tax_and_quote_override_columns.sql | H4.3/H4.4 | `order_line_items.tax_rate_percent`; `cart_line_items.unit_price_override` |
| V34 | financial_amount_check_constraints.sql | H4.5 | Non-negative CHECKs across orders/order_line_items/payments/refunds/return_line_items/gst_notes/coupons/margin_rules/statements |
| V35 | financial_retention_restrict_fk.sql | H4.6 | CASCADE→RESTRICT: payments→orders, invoices→orders, returns→orders, refunds→returns, gst_notes→returns, statements→companies |
| V36 | gst_sequences_fiscal_year_pk.sql | H4.7 | `gst_sequences` PK → composite `(sequence_type, fiscal_year)` |
| V37 | missing_operational_indexes.sql | H5.1 | 7 hot-path indexes |
| V38 | catalog_outbox_retry_and_attempt_columns.sql | H5.2 | `attempt_count`/`last_attempt_at`/`error_message`; terminal `FAILED` status |
| V39 | drop_dead_plaintext_user_indexes.sql | H5.3 | Drops plaintext unique indexes superseded by V22 blind indexes |
| V40 | orders_slot_configuration_fk.sql | H5.1 | `fk_orders_slot` (RESTRICT) — previously no FK |
| V41 | create_payment_reconciliations.sql | H9.1 | `payment_reconciliations` (unique order_id+type) |

No migrations exist outside `db/migration/postgresql/`; tests reuse the same path (no test-only migration set).

### 10.3 Database Invariants — Constraint-to-Business-Rule Mapping

| Constraint | Table | Business rule enforced |
|---|---|---|
| `uq_returns_one_active_per_order` (partial, `status<>'REJECTED'`) | returns | One active return per order at a time |
| `uq_delete_requests_pending_user` (partial, PENDING) | delete_requests | One pending account-deletion request per user |
| `excl_contract_pricing_no_overlap` / `excl_company_contract_pricing_no_overlap` (GiST EXCLUDE) | contract_pricing / company_contract_pricing | No overlapping effective-date pricing windows per (user\|company, product) |
| `uq_rfq_quotes_rfq_vendor` | rfq_quotes | One quote per vendor per RFQ |
| `uq_aa_request_type_stage` | approval_actions | One action of a given type per approval stage |
| `uq_po_imports_company_key` | po_imports | Idempotency key reuse blocked per company, even after structural failure |
| `uq_gst_notes_return_type` | gst_notes | At most one CREDIT and one DEBIT note per return |
| `chk_slot_counter_capacity` (`0<=current_count<=capacity`) | delivery_slot_counters | Hard DB-level overbooking cap, independent of application lock discipline |
| Composite PK `(sequence_type, fiscal_year)` | gst_sequences | GST numbering scoped per fiscal year — prevents cross-year collisions |
| `uq_invoices_order_id`, `uq_invoices_number` | invoices | One invoice per order; globally unique invoice numbers |
| `uq_statements_company_period_version`, `uq_statements_company_number` | statements | At most one row per (company, period, version); unique numbers per company |
| `uq_payment_reconciliations_order_type` | payment_reconciliations | One durable work item per (order, reconciliation type) |
| `uq_refunds_gateway_refund_id` | refunds | A gateway refund id backs at most one local refund row |
| `chk_company_members_role` / `chk_crp_role` / `chk_crp_permission` | company_members / company_role_permissions | Role/permission vocabularies enforced at DB level, not just application enums; OWNER excluded from the permission table entirely |
| `ON DELETE RESTRICT` (V35) | payments/invoices/returns/refunds/gst_notes/statements → parent | Financial records cannot be deleted merely because a parent row is deleted |
| `fk_orders_slot` (V40, RESTRICT) | orders → slot_configurations | A slot referenced by any historical order can never be deleted |

**No entity anywhere declares `@Version`** — optimistic locking is not used at all in this codebase; every concurrency-safety mechanism is pessimistic lock, CAS conditional UPDATE, native upsert, or a unique/exclusion constraint caught as `DataIntegrityViolationException`. Full mechanism-by-mechanism detail (with exact repository/adapter methods) is in §19.


## 11. Business Workflows

Each workflow below traces the real call chain (controller → application service → domain → persistence/external I/O) with exact class/method names, verified from source.

### 11.1 Authentication (see §8.1–8.2 for full detail)
`AuthController` → `AuthServiceImpl` → `OtpSendService`/`OtpVerificationService`/`GoogleIdentityGateway` → `UserServiceImpl.findOrCreateBy{Phone,Google}` → guest-cart merge (`CartService.mergeGuestCart`, failure logged as warning, never fails login) → `LoginEventServiceImpl.record` → `JwtTokenIssuer` + `DeviceServiceImpl.create`.

### 11.2 Cart lifecycle
`CartController` → `CartServiceImpl`. `upsertItem()`: validates product exists → `getOrCreateCart()` (by `userId`+`projectId`, `CartType.PRIMARY`) → atomic native upsert (`CartLineItemJpaRepository.upsert`, `INSERT...ON CONFLICT...DO UPDATE`) or delete for qty≤0 → `CartPricingCalculator.calculate()` (the Phase-2 pricing engine: `PricingCalculatorImpl` running the `PricingStep` chain in `PricingSteps` — base price → bulk tier → contract price → margin floor → GST → coupon). Cart types: `PRIMARY`, `REORDER_SCRATCH` (short-lived, no cleanup sweeper exists — flagged as a `ponytail:` deferred item in code), `B2B_DRAFT` (company-scoped, `projectId` = source RFQ/PO id). `claimForCheckout(cartId)` is the H2.1 one-time-consumption CAS (`consumed_at IS NULL` conditional UPDATE) preventing a B2B draft cart from being checked out twice concurrently.

### 11.3 Checkout → Order creation
`OrderController.createOrder` → **`OrderServiceImpl.create()`** — the most heavily orchestrated method in the codebase, running inside one `TransactionTemplate.execute()` block:
1. Idempotency check (`IdempotencyKeyRepository.findOrderId`, rolling window `orders.idempotency-window-hours`, default 24h).
2. For a B2B draft cart: **lock order is COMPANY → SLOT_COUNTER** — `B2bAuthorizer.authorize(critical=true)` (locks company row) runs *before* `CheckoutIntentService.createIntent()` (locks slot counter). `cartService.claimForCheckout(cartId)` runs first of all — a lost claim throws `BadRequestException("CART_ALREADY_CONSUMED")` before any other side effect. `siteId` mandatory for B2B (`BadRequestException("SITE_REQUIRED")`).
3. `CheckoutIntentService.createIntent()` prices the cart and acquires a delivery-slot lock (§11.4).
4. `ApprovalGateService.evaluate()` for B2B orders (§11.7).
5. Builds `Order`: `PENDING_APPROVAL` (no slot-lock id — released by the gate) if gated, else `PAYMENT_PENDING` (holding the slot-lock id).
6. Saves order + idempotency key + coupon redemption rows.
7. If gated: `ApprovalGateService.openApproval()` (releases the slot lock immediately, creates `ApprovalRequest`). If not gated: creates a durable `Payment{status=PENDING}` row **in the same transaction** as the order (so a crash after this point leaves evidence of the attempt rather than an orphan order).
8. Concurrent double-submit (`DataIntegrityViolationException`/`TransactionException` on the idempotency-key unique constraint): the losing transaction rolled back entirely; the method polls `existingOrderForKey()` (up to 20×50ms) for the winner's order.
9. **Outside** the transaction: `completePaymentInitiation()` calls `PaymentGateway.initiate()` with no open transaction/lock held; success/failure write into a second short transaction.
10. `cartService.clearCart()` for non-B2B-draft checkouts only (a claimed B2B draft stays consumed, never re-cleared as PRIMARY).

`retryPayment()`/approval-resume both lock the order row, require `PAYMENT_PENDING`, and reject a second in-flight attempt (`PaymentRetryInProgressException`). **Reorder**: B2B orders explicitly rejected (`BadRequestException("B2B_REORDER_UNSUPPORTED")`) since `createReorderCart` produces a `companyId=null` cart that would silently bypass approval/contract pricing.

### 11.4 Delivery slot allocation
`DeliverySlotServiceImpl` — every mutation locks the `DeliverySlotCounter` row (`findBySlotIdAndSlotDateForUpdate`) before touching capacity.
- **`acquireOrSwapLock()`**: extends TTL if the user already holds an ACTIVE lock on the same slot/date; for a swap, locks both counters in **canonical order** (`lockCountersCanonical()` — compares `slotId` then `slotDate`) so concurrent opposite-direction swaps cannot deadlock (H2.5). Old-lock release is a CAS (`tryTransitionStatus(ACTIVE→RELEASED)`) — counter only decrements if this call actually won the transition.
- **`consumeLock()`**: CAS `ACTIVE→CONSUMED`, no counter decrement (confirmed order retains capacity through delivery). Returns `boolean` — `false` is a **critical reconciliation signal** for the payment webhook, not a silent success.
- **`releaseLock()`**: CAS `ACTIVE→RELEASED`, counter locked before the lock row, matching the same global order.

### 11.5 B2B company / RBAC — see §8.7 in full.

### 11.6 RFQ lifecycle
`RfqController`/`RfqQuoteAdminController` → `RfqServiceImpl`. `create()`: authorizes `RFQ_CREATE`, validates items, computes a **creation-time vendor-routing snapshot** (`VendorRepository.findRoutableVendors` — any category match; empty leaves the RFQ OPEN with no routes). `submitQuote()` (vendor-side, admin-submitted): locks RFQ row, requires OPEN, vendor must be routed (`VendorNotRoutableException`), one quote per (rfq,vendor) (`DuplicateQuoteException`). `convert()`: lock RFQ → authorize `RFQ_CONVERT` (critical) → require OPEN → validate quote → **H4.3 price-allocation**: distributes the vendor's single total across line items proportionally to catalog base price using integer-cent-floor + largest-remainder rounding (sum equals the quote total exactly, no drift) → `CartService.createB2bDraftCart(...)` with `unitPriceOverride` per line → flip RFQ to `CONVERTED`, all in one transaction. Expiry: `RfqExpirySweeper` — one conditional bulk `UPDATE...WHERE status='OPEN' AND expires_at<now()` every 60s; correctness comes from the UPDATE's own atomicity, not any application lock.

### 11.7 Approval workflow (B2B order gating)
`ApprovalGateServiceImpl.evaluate(companyId, orderTotal, productIds, siteId)`: loads the company's single `ApprovalPolicy` (none = not gated). Three OR'd match rules: `AMOUNT` (`orderTotal >= threshold`, inclusive), `CATEGORY` (line-item category intersects policy categories), `SITE` (fail-closed if policy has site constraints and `siteId==null` — though checkout's mandatory-siteId check makes this unreachable in practice). `openApproval()` (`@Transactional(MANDATORY)` — must run inside the caller's transaction) releases the just-acquired slot lock (capacity must not be held while pending), creates an `ApprovalRequest` at stage 0, publishes `ApprovalRequestedEvent`. `ApprovalServiceImpl.approve()`: lock order **COMPANY → ORDER → REQUEST → (approve only) SLOT_COUNTER**, `TransactionTemplate`-wrapped so a `SlotUnavailableException` can be captured and rethrown *after* the transaction commits (the slot-failure cancellation must still commit). Escalation: `ApprovalEscalationJob` (60s) → `ApprovalEscalationServiceImpl.escalateOne` (row-locked, re-checks `escalationDueAt`).

### 11.8 Purchase Orders (PO)
Two surfaces, no `PurchaseOrder` entity: (a) **`PoAttachmentServiceImpl`** — durable-claim upload of an XLSX document against an existing B2B order (§6.3 pattern, explicitly modeled on refund-gateway discipline); B2C orders rejected with a masked 404 (not 403, to avoid revealing order existence to a non-B2B caller); file validated by magic-byte ZIP signature (`50 4B 03 04`), 2MB cap, no path separators/NUL bytes in filename. (b) **`PoImportServiceImpl`** — bulk XLSX import: idempotency-key replay check → CPU-only streaming SAX parse+validate (no DB connection held) → persist import+rows+draft-cart in a second transaction. A structural parse failure still persists a `FAILED_STRUCTURE` row, consuming the idempotency key. Row validation resolves product slugs, caps at 10,000 units per merged-by-slug group, treats slug ambiguity as a hard row error. `PoConversionServiceImpl.convert()`: REVIEW import → `B2B_DRAFT` cart, idempotent (already-CONVERTED returns the existing cart id).

### 11.9 Payment — webhook processing
`PaymentWebhookController` → **`PaymentWebhookServiceImpl.handleWebhook`**, `@Transactional`: fail-closed HMAC verification → `findByIdForUpdate(orderId)` (same lock choke point as the stale-order sweeper, H1.5 — deterministic winner). On `SUCCESS` arriving on an already-CANCELLED order: `recordCapturedPaymentOnCancelledOrder()` — marks Payment SUCCESS (durable evidence) and writes a `PaymentReconciliation{type=CAPTURED_ON_CANCELLED_ORDER, status=FLAGGED_MANUAL}` row for **manual compensating refund**; the order is never resurrected. On `SUCCESS` on `PAYMENT_PENDING`: refuses to confirm if no `Payment` row exists at all (treats this as an integrity anomaly rather than trusting the webhook alone); otherwise `order.confirm()`, `payment.markSuccess()`, creates an `Invoice{PENDING}` row if none exists (same transaction as confirmation), publishes `OrderConfirmedEvent`, calls `DeliverySlotService.consumeLock()` (a `false` return logs CRITICAL for manual reconciliation rather than rolling back an already-paid order).

**Reconciliation**: `PaymentReconciliationServiceImpl.reconcileStalePendingPayment()` — called by the stale-order sweeper before cancelling; queries `PaymentGateway.queryStatus()` outside any transaction; gateway SUCCESS confirms the order (same sequence as the webhook), FAILED marks payment failed, anything ambiguous returns `AMBIGUOUS_HOLD` (sweep skips cancellation entirely).

`StaleOrderSweepServiceImpl.sweepStaleOrders()`: per stale order, reconciles first, only cancels (`self.sweepOrder()`, `REQUIRES_NEW` via a `@Lazy` self-injection proxy) if `CANCEL_ELIGIBLE`. Slot-lock release happens in a **separate** `REQUIRES_NEW` transaction (`self.releaseLockForOrder()`) — deliberately avoiding "the rollback trap" where a nested-call exception would mark the *whole* transaction (including the already-decided cancellation) rollback-only. Proven on real Postgres by `StaleOrderSweepRollbackJpaIT` (§18).

### 11.10 Refund
Triggered by QC pass (`ReturnServiceImpl.passQc()` commits the QC transition, then **outside** that transaction calls `RefundServiceImpl.initiateRefund()`). Three phases mirroring the payment-gateway discipline:
1. **Claim** (own tx): lock `Return`, require `QC`, reject if a non-FAILED `Refund` already exists (`FAILED` claims don't block retry), compute amount via `ReturnRefundCalculator.calculateTotalRefund()`, persist `Refund{PENDING}`.
2. **Gateway** (no tx): `PaymentGateway.refund()`. `GatewayRejectedException` (certain rejection) → mark FAILED, safe to retry. Any other exception (ambiguous, H1.6) → **left PENDING**, never FAILED, because FAILED is retry-eligible and a retry on an ambiguous outcome risks a second real refund.
3. **Finalize** (own tx): lock `Return` then `Refund` (same canonical order the webhook uses) — a concurrent webhook that already reached a terminal state makes this a no-op (SUCCESS is monotonic). Otherwise `Return: QC→REFUND_INITIATED`, saves the gateway's refund id but **keeps status PENDING** (terminal transition belongs exclusively to the refund webhook).

**Webhook** (`RefundWebhookServiceImpl.handleWebhook`): HMAC over `"returnId:gatewayRefundId:status"`, fail-closed. Looks up by `gatewayRefundId` first, falls back to `returnId`. Locks `Return` then `Refund` (canonical order). Idempotency via re-checking `refund.status()` after the lock — no unique constraint needed, the row lock itself serializes duplicate deliveries. On SUCCESS: marks refund SUCCESS, `Return→REFUND_COMPLETED`, publishes `ReturnStatusChangedEvent`, and — only on this one winning path — mints a GST **credit note** (`GstSequenceService.nextNumber(CREDIT_NOTE)`). `uq_gst_notes_return_type` is described in code as "a pure backstop, not the concurrency mechanism" — the row-lock re-check is what actually prevents duplicates.

### 11.11 Return / QC lifecycle
`ReturnController` → `ReturnServiceImpl.createReturn()`: requires ≥1 valid photo (jpeg/png/webp); resolves order ownership (B2B via `B2bAuthorizer`, consumer via direct match, non-owner gets 404 not 403); requires `order.status()==DELIVERED`; duplicate-return guard (`findActiveByOrderId` pre-check + `uq_returns_one_active_per_order` DB backstop, H3.4); per-line-item return window anchored to the latest DELIVERED tracking event (fallback `order.placedAt()`), length per-category (`Category.returnWindowDays`, default 7). Photos uploaded to S3 under `returns/{returnId}/photos/{uuid}.{ext}` before the `Return` row saves. Mutating transitions (`approve`/`schedulePickup`/`pickUp`/`passQc`/`reject`) require ADMIN or VENDOR role, lock the row, apply the guarded domain transition (§9.9).

### 11.12 Invoice generation + GST sequence
Invoice row created eagerly (`status=PENDING`) in the same transaction as order confirmation by `PaymentWebhookServiceImpl`/`PaymentReconciliationServiceImpl` (H8.2). Async generation: `InvoiceGenerationScheduler` (60s) + `InvoiceDlqWorker` (RabbitMQ DLQ consumer + 2h backstop sweep) drive **`InvoiceGenerationServiceImpl.processInvoice()`**, a three-phase shape:
1. **Claim** (short tx, row-locked): terminal states no-op; a fresh GENERATING claim (<15min old) is treated as another worker's active ownership (attempt count not bumped); ≥6 total attempts → straight to FAILED; otherwise `invoice.claim()` bumps attempt count.
2. **Slow I/O** (no tx): `InvoiceSnapshotBuilder.build()` assembles the snapshot, `OpenPdfInvoiceRenderer.render()` produces PDF bytes, `ObjectStorage.store()`.
3. **Commit** (short tx, row-locked): re-checks READY (no-op if a racing worker already committed), mints the number via `GstSequenceService.nextNumber(INVOICE)`, `invoice.markReady()`, publishes `InvoiceReadyEvent`.

**GST sequence** (`application/service/GstSequenceService`, a concrete `@Service`, not the interface+impl convention used elsewhere): fiscal year is India-standard April–March, computed in `Asia/Kolkata`. Number format `{PREFIX}-{shortFY}-{6-digit padded}` (e.g. `INV-2526-000001`). **Primary path**: a single atomic native SQL statement (`INSERT...ON CONFLICT(sequence_type,fiscal_year) DO UPDATE SET current_val=current_val+1...RETURNING current_val` via `JdbcTemplate`) — no read-then-write race window, safe under arbitrary concurrency because Postgres serializes the `ON CONFLICT` clause per row. **Fallback path** (only if `JdbcTemplate` is unavailable, e.g. mocked in unit tests): JPA row-locked read+increment+save. Both paths share `nextNumber()`'s `@Transactional` boundary — a caller-side rollback (e.g. invoice-commit save fails after `nextNumber()` succeeded) reverts the increment too. **The guarantee is "no gap survives a rolled-back transaction," not "no gaps ever"** — an invoice claim that permanently FAILs after 6 attempts consumes a sequence number never printed on a document, which is an accepted, documented GST-numbering gap (GST rules tolerate voided/cancelled number gaps).

### 11.13 Monthly statements
`StatementScheduler` (5min) → `StatementGenerationServiceImpl.generateDue()`/`recoverStuck()`. `generateDue()`: iterates ACTIVE companies (suspended companies generate nothing) × closed months not yet READY/actively-GENERATING, bounded by `sweepBatchLimit`. `startGeneration()` (claim tx): locks Company row first (same global order as `B2bAuthorizerImpl`), checks for existing READY/fresh-GENERATING version, else inserts GENERATING (version 1) or reclaims stale — `UNIQUE(company_id,period_start,period_end,version)` is the multi-instance backstop, caught *after* the transaction boundary (a documented "9-C lesson": don't try to translate a same-transaction constraint violation). Empty-month rule: zero orders and zero credit notes → nothing generated at all. **Artifact generation** (no tx held): pages order/line rows (500/1000 per page), **cross-checks** paged sums against SQL-aggregated totals before rendering (`IllegalStateException` on mismatch rather than a covertly-wrong statement), renders PDF + streaming-SXSSF XLSX, stores both to S3. **Finalize** (short tx): re-checks READY, mints number via `StatementSequenceService` (a separate per-company sequence from the GST table), computes `net=gross-tax`/`due=gross-creditTotal`, `markReady()`.

### 11.14 Notifications
`NotificationServiceImpl.notify()`/`notifyRecurring()` — outbox-style: dedupe guard (`existsByEventType...`, per-user for one-shot events, cooldown-windowed for recurring), durable `NotificationLog{PENDING}` row (captures a **phone snapshot** at dispatch time, not a live join), `NotificationDispatchQueue.enqueue()` → RabbitMQ. A user with no phone is skipped (info log, never fails the caller's transaction). Consumed by `infra/consumer/NotificationDispatchListener` → channel-specific sender port (all dev/logging stubs except none confirmed real — see §16).

### 11.15 Search / catalog indexing (outbox)
`CatalogWriteServiceImpl.saveProductAndEnqueueSync()` writes the product row and a `catalog_outbox_events` row atomically (classic transactional outbox). `CatalogOutboxRelay` (5s) relays up to 50 pending rows via `CatalogEventPublisher.publishProductChanged()` (RabbitMQ, publisher-confirmed) — bounded retry (5 attempts, then terminal FAILED, "poison rows never block siblings"). `CatalogProductChangedListener` consumes, upserts into the Elasticsearch `products` alias with **external versioning** (stale/out-of-order writes silently lose on a 409, last-writer-wins by timestamp), republishes a confirmation message consumed by `CatalogIndexedConfirmationListener` which marks the outbox row PROCESSED. `CatalogReindexer` (nightly 2am, dev override every 30s) performs a full **blue-green reindex** (new timestamped index → bulk-index all products → atomic alias swap → best-effort old-index delete) and doubles as an outbox-drift reconciliation pass.

### 11.16 Account deletion / PII (DPDP)
`UserController.requestDeletion` → `DeleteRequestServiceImpl` (409 if already pending, backed by `uq_delete_requests_pending_user`). `AccountDeletionSweeper` (nightly 02:30, batch 50): per-table classification — **RETAIN** (orders, payments, refunds, returns, gst_notes, invoices+PDFs, idempotency_keys, contract_pricing — tax/compliance), **HARD-DELETE** (addresses referenced by orders are anonymized in place, not deleted, since `orders.address_id` is NOT NULL; unreferenced addresses hard-deleted; carts, coupon redemptions, wishlist, notify-me, search logs, devices, login events, notification logs, delivery-slot locks; support tickets/messages config-gated `HARD_DELETE`/`RETAIN`), **ANONYMIZE** (`users` row — identity + blind-index columns nulled, row retained for FK/audit integrity). Per-table try/catch (one failing table doesn't block siblings; the whole request stays PENDING for retry, and every delete is idempotent). Active slot locks explicitly released via the hardened CAS path *before* their rows are hard-deleted (prevents leaking held capacity). Return photos deleted from S3 first.

---

## 12. Payment Architecture

### 12.1 `PaymentGateway` port and its only implementation

`domain.port.PaymentGateway{initiate, refund, queryStatus}` has **exactly one implementation in the entire codebase**: `infra/gateway/DummyPaymentGatewayAdapter`, annotated `@Profile("!prod")`. **No `@Profile("prod")` or unconditional implementation exists anywhere** (verified via `grep -rl "implements PaymentGateway"`). Running with `spring.profiles.active=prod` (or any profile set excluding the `!prod` match) leaves no Spring bean to satisfy this `@Autowired` dependency and **the application context will fail to start**. This is the single most significant production-readiness gap in the repository (see §27, §28) — no real payment provider (Razorpay, Stripe, Cashfree, etc.) is integrated.

`DummyPaymentGatewayAdapter` behavior (documents the contract a real adapter must satisfy):
- `initiate(orderId, amount)`: fabricates a `PaymentReference(txId, url)`; amount exactly `9999` simulates a `RuntimeException` ("gateway connection timeout"); otherwise schedules an async in-process `PaymentWebhookEvent` (2-second delay) carrying `SUCCESS` and a fake signature.
- `refund(transactionId, amount, returnId)`: amount `9999` → `AmbiguousGatewayException` (explicitly modeled as "transport failure, not a rejection"); `9998` → simulated FAILED; else SUCCESS via a delayed `RefundWebhookEvent` echoing `returnId` (durable correlation key for crash recovery via `findByReturnId`).
- `queryStatus(transactionId, orderId)`: magic-substring-driven (`"fail"`→FAILED, `"timeout"`→empty/unknown, `"pending"`→PENDING, else SUCCESS) or an in-memory `simulatedOrderStatuses` map — backs the reconciliation scheduler.

`PaymentWebhookEvent`/`RefundWebhookEvent` (the in-process `ApplicationEventPublisher` records) are published **only** from `DummyPaymentGatewayAdapter` — confirmed by repo-wide grep — meaning they are dummy-gateway simulation scaffolding, not a live production event path; a real gateway integration would call the real `/api/webhooks/payment` and `/api/webhooks/refund` HTTP endpoints instead (§7.1).

### 12.2 Idempotency

- **Order creation**: `Idempotency-Key` header, `idempotency_keys` table, rolling window (default 24h, purged nightly by `IdempotencyPurgeScheduler`). Raw `JdbcTemplate` INSERT (not JPA `save`), specifically to force a unique-constraint violation on double-submit rather than a silent `merge()` upsert.
- **Payment retry**: at most one PENDING payment attempt in flight per order (`PaymentRetryInProgressException`).
- **Refund claim**: at most one non-FAILED `Refund` per return.
- **Webhook delivery**: idempotency without a unique constraint — the row lock (order/return+refund) plus a post-lock re-check of current status makes every delivery after the first a no-op.

### 12.3 Payment state machine

`PaymentStatus`: PENDING → SUCCESS | FAILED, **unguarded at the domain-record level** (§9.9) — the application layer (webhook/reconciliation services) is solely responsible for not overwriting a terminal state. `RefundStatus` is identical in shape.

### 12.4 Webhook signature/authentication

Both payment and refund webhooks: HMAC-SHA256 over a canonical string (`"orderId:status"` / `"returnId:gatewayRefundId:status"`), `MessageDigest.isEqual` constant-time compare, fail-closed (missing/blank secret or signature → 401 unconditionally, no dev bypass). Secret has no config default (`@NotBlank`, fails startup if unset).

### 12.5 Ambiguous failures

Both the refund-gateway call and the order payment-status reconciliation treat an unclassified/exception outcome as **ambiguous, not failed**: refund claims stay PENDING (never auto-marked FAILED, since FAILED is retry-eligible and a second attempt risks double-refunding); stale-order reconciliation returns `AMBIGUOUS_HOLD` and skips cancellation entirely rather than risk cancelling an order whose payment may still succeed upstream.

### 12.6 Provider status reconciliation / cancelled-order reconciliation

`PaymentReconciliationServiceImpl.reconcileStalePendingPayment()` queries `PaymentGateway.queryStatus()` **outside any transaction** before the stale-order sweeper cancels an order. A `SUCCESS` webhook arriving on an already-cancelled order is handled by `recordCapturedPaymentOnCancelledOrder()` — money captured is recorded durably (`Payment.markSuccess` + a `PaymentReconciliation{CAPTURED_ON_CANCELLED_ORDER, FLAGGED_MANUAL}` row) but the order is never automatically resurrected; a human must reconcile (issue a manual compensating refund or otherwise resolve the flagged row). `payment_reconciliations` (V41) is the durable table backing this — `uq_payment_reconciliations_order_type` ensures one work item per (order, type) rather than duplicate rows on repeated webhook deliveries.

### 12.7 Refund idempotency — see §11.10.

### 12.8 Gateway calls vs. DB transactions — crash windows

Every gateway call (payment initiate, refund, status query) runs **outside** any open database transaction/row lock (§6.3). The crash windows this leaves, and how each is closed:
- **Crash between claim-commit and gateway call**: the durable PENDING claim survives; a retry/reconciliation sweep picks it up.
- **Crash between gateway success and finalize-commit**: for refunds, `findByReturnId`/gateway-echoed `returnId` lets a later reconciliation correlate the gateway's own record back to the local claim. For payment confirmation, the webhook is the authoritative signal — if it never arrives, the stale-order sweeper's `queryStatus()` reconciliation catches it before cancelling.
- **Ambiguous gateway response**: never auto-resolved to a terminal state — always parked (PENDING refund claim / `AMBIGUOUS_HOLD` order) for manual or later-automated reconciliation, never silently retried into a possible double-charge/double-refund.

### 12.9 Manual reconciliation states

`PaymentReconciliation{status=FLAGGED_MANUAL}` rows are the durable, queryable record of every situation requiring human intervention (payment captured on a cancelled order being the concrete case implemented). No API endpoint to list/resolve these was found in the controller inventory (§7) — resolution appears to be a direct-database or future-tooling operation, **not found / not verifiable** as an exposed workflow in this repository.

### 12.10 Remaining provider integration prerequisites

To go to production, a deploying team must, at minimum:
1. Implement a `@Profile("prod")` (or unconditional) `PaymentGateway` adapter against a real provider (Razorpay/Stripe/Cashfree/etc.), matching the `initiate`/`refund`/`queryStatus` contract exhibited by `DummyPaymentGatewayAdapter`.
2. Wire that provider's real webhook delivery to `POST /api/webhooks/payment` and `POST /api/webhooks/refund`, with `PAYMENT_WEBHOOK_SECRET` matching the provider's actual signing mechanism (the current HMAC-over-a-fixed-string scheme is this repo's own invention for the dummy adapter and would need to match whatever the real provider signs).
3. Decide whether `PaymentWebhookEvent`/`RefundWebhookEvent` (currently dummy-only in-process events) have any role in a real integration, or should be removed once real webhook delivery replaces them.


## 13. Accounting, Invoicing and GST

### 13.1 Invoice lifecycle
State machine: PENDING → GENERATING → READY (or DLQ_RETRY → FAILED on exhaustion). Row created eagerly at order confirmation (§11.12), populated asynchronously by the three-phase claim/render/commit shape. `uq_invoices_order_id` and `uq_invoices_number` guarantee exactly one invoice per order and global number uniqueness.

### 13.2 Invoice number generation / fiscal year handling
`GstSequenceService.nextNumber(GstSequenceType)` — see §11.12 for the full mechanism. Fiscal year: India-standard April–March, computed in `Asia/Kolkata`. Format `{PREFIX}-{shortFY}-{6-digit}` — `INV-` for invoices, `CRN-` for credit notes, `DBN-` for debit notes (debit-note code paths exist in the type enum; no calling workflow producing a debit note was found in the traced business workflows — **not found / not verifiable** whether debit notes are ever actually generated by any current flow, as opposed to being a modeled-but-unused capability).

### 13.3 GST sequence implementation
Composite PK `(sequence_type, fiscal_year)` on `gst_sequences` (V36, replacing a pre-V36 schema keyed only by `sequence_type`, which allowed numbering collisions across fiscal years). Primary allocation path is a single atomic native `INSERT...ON CONFLICT...DO UPDATE...RETURNING` (no row lock needed — Postgres serializes the `ON CONFLICT` clause per row); a JPA-pessimistic-lock fallback exists for contexts where `JdbcTemplate` is unavailable. The transactional guarantee is explicitly "no gap survives a rolled-back transaction" — not "no gaps ever": a permanently-FAILED invoice claim (6 attempts exhausted) consumes and burns a sequence number that never appears on an issued document. This is a documented, accepted gap (GST practice tolerates voided/cancelled number gaps), not a defect. Verified by test classes `GstSequenceConcurrencyJpaIT`, `GstSequenceIntegrityJpaIT`, `GstSequenceRollbackJpaIT`, `GstSequenceFiscalYearRolloverJpaIT`.

### 13.4 Tax snapshots / order-line financial snapshots
`OrderLineItem.taxRatePercent` (V33) snapshots the GST rate applying at order time, independent of any later change to `hsn_gst_rates` — so a rate change never retroactively alters an already-placed order's tax. `OrderLineItem.lineTotal` (V15) is the GST-inclusive charged total; `unitPrice` is derived (`lineTotal/quantity`, rounded) and can drift by paise from a naive `unitPrice×quantity` recomputation — an explicit, documented rounding-precision disclosure in the domain model's own Javadoc. `OrderInvoiceSnapshot`/`InvoiceLineItemSnapshot` freeze the full invoice-relevant order state (lines, buyer/seller GST details) at generation time, fed to `OpenPdfInvoiceRenderer`.

### 13.5 Coupon allocation
`CouponRedemption` rows persist one per applied coupon (cart-level and per-item) at order-creation time so `maxUsesPerUser` limits hold across concurrent checkouts; the redemption count is allowed to over-count slightly on the safe side if a later pricing step absorbed the discount differently. `CartCouponProRataAllocationTest` covers the pro-rata allocation math specifically.

### 13.6 Refund accounting / credit-debit notes
A GST **credit note** is minted (`GstSequenceService.nextNumber(CREDIT_NOTE)`) exactly once, on the single winning refund-webhook `SUCCESS` delivery (§11.10) — never on the refund-initiation claim, since only a completed refund is a real accounting event. `uq_gst_notes_return_type` (V31) is a documented backstop, not the actual concurrency mechanism (the row-lock re-check in `RefundWebhookServiceImpl` is what prevents duplicates). Debit notes: modeled in `GstNoteType` and the sequence-type vocabulary, but no traced workflow generates one — see §13.2.

### 13.7 Monthly statements — see §11.13 for the full generation trace.
Distinguishing points for accounting purposes: statement figures are **cross-checked** (paged line-row sums vs. SQL-aggregated totals) before any PDF/XLSX is rendered, failing loudly (`IllegalStateException`) on mismatch rather than emitting a silently-wrong statement. `StatementDiscrepancy` rows flag `INVOICE_MISSING`/`INVOICE_NOT_READY` per order — a statement can be generated even when some constituent orders' invoices aren't yet ready, with the gap explicitly recorded rather than silently omitted. Statement numbering (`StatementSequenceService`, `statement_sequences` table, composite PK `(company_id, period_key)`) is **deliberately not gapless** by design (a failed generation attempt just skips a number) — a looser guarantee than the GST sequence table, appropriate since statements are informational summaries, not the GST-numbered legal documents invoices/notes are.

### 13.8 Financial immutability / retention rules
V35 (`financial_retention_restrict_fk.sql`) changed the FK behavior from CASCADE to **RESTRICT** on payments→orders, invoices→orders, returns→orders, refunds→returns, gst_notes→returns, statements→companies — a parent row (order, return, company) can no longer be deleted while financial children exist, converting what would have been silent data loss into a loud failure. V34 (`financial_amount_check_constraints.sql`) adds non-negative `CHECK` constraints across orders/order_line_items/payments/refunds/return_line_items/gst_notes/coupons/margin_rules/statements — the database itself refuses a negative monetary value regardless of application-layer bugs. The account-deletion sweeper's RETAIN classification (§11.16) keeps every financial table (orders, payments, refunds, returns, gst_notes, invoices+PDFs, contract_pricing) untouched even when the associated user is deleted — personally-identifying fields on the retained rows are anonymized at the `users`-row level (§11.16), not by deleting the financial rows themselves.

### 13.9 Transactional guarantees vs. operational assumptions — summary
**Implemented and DB-enforced**: fiscal-year-scoped gapless-within-a-committed-transaction GST numbering; one invoice per order; exactly one credit/debit note per type per return; non-negative financial amounts; RESTRICT-protected financial retention.
**Operational assumption / accepted gap**: sequence numbers can be burned by a permanently-failed generation attempt (never printed, never reused) — acceptable under GST practice but requires the deploying organization to understand this if reconciling printed invoice numbers against the sequence counter. Statement numbering is explicitly non-gapless. Debit-note generation exists in the data model but no code path was found to trigger it.

---

## 14. B2B Architecture

### 14.1 Companies, sites, membership
`Company` (id, name — deliberately not unique, "real-world duplicates are legal", identity is `id`; gstNumber, statementEmail, businessTimezone, status: ACTIVE/SUSPENDED). `CompanyMember` (companyId, userId, role — a user may belong to multiple companies; uniqueness is per-company `(companyId,userId)`, never global). `CompanySite` (companyId, name, addressId, active — deactivation not deletion is the lifecycle end; a site referenced by any non-CANCELLED order cannot be deactivated, enforced under row lock).

### 14.2 Roles and the 22-permission vocabulary
`CompanyRole` includes `OWNER` (implicit all-permissions, never stored, cannot be edited/locked-out) plus customizable roles with hardcoded creation-time defaults (`CompanyPermissionDefaults`, consulted only once at company creation — `company_role_permissions` rows are sole source of truth thereafter). Full permission list and enforcement point: §8.7. Role/permission vocabularies are enforced **both** in application enums and by DB `CHECK` constraints (`chk_crp_role`/`chk_crp_permission`) — the CHECK excludes `OWNER` from the permission table entirely, making an OWNER-permission row structurally unrepresentable.

### 14.3 Owner behavior / role management
Last-OWNER protection (`LastOwnerProtectedException`) and OWNER-permissions-immutable (`OwnerPermissionsImmutableException`) are both enforced in `CompanyMembershipServiceImpl` under the documented lock sequence: (1) `B2bAuthorizer` locks the company row, (2) `findByCompanyIdForUpdate` locks **all** member rows `ORDER BY id`, (3) invariant evaluated under that lock, (4) mutate+commit. `addMember` catches the `UNIQUE(company_id,user_id)` violation and rethrows as `MemberAlreadyExistsException` — the DB constraint is the actual concurrency guard, the pre-check is best-effort only. `transferOwnership`: old OWNER is demoted to `PROCUREMENT_MANAGER`, not removed.

### 14.4 Suspension
Enforced in exactly one place: `B2bAuthorizerImpl.authorize(critical=true)` — a suspended company's every critical mutation is rejected with `ForbiddenException("COMPANY_SUSPENDED")` before any other check runs. Read-only (`critical=false`) calls are **not** blocked by suspension (verified from `B2bAuthorizerImpl`'s branching — suspension check only fires inside the `critical` branch), so a suspended company's members can still view existing data. `SuspendedCompanyMatrixIT` covers this boundary.

### 14.5 Pricing (company contract pricing)
`CompanyContractPrice` — company-scoped negotiated pricing, additive above any user-level `ContractPrice` tier, non-overlapping effective-date windows enforced by a GiST exclusion constraint (`excl_company_contract_pricing_no_overlap`, V25) in addition to an application-level pre-check (§19).

### 14.6 Approval policies and escalation — see §11.7 for the full trace.
`ApprovalPolicy` (one per company, `version` incremented on replace, prior versions never mutated). `ApprovalRequest` snapshots the matched-rule details, threshold, category/site match, role stages, and escalation hours **at creation time** — later policy edits or catalog price changes never retroactively affect an in-flight request. Approver eligibility, by contrast, is **not** snapshotted — resolved live at action/notification time, so a member removed from the company mid-approval is silently excluded from further action/notification without any special-case code.

### 14.7 B2B cart / RFQ / quotes / purchase orders / reorder
- **B2B cart**: `CartType.B2B_DRAFT`, `companyId` set, `projectId` carries the source RFQ or PO-import id, one-time-consumption CAS at checkout (§11.2).
- **RFQ / quotes**: §11.6.
- **Purchase orders**: §11.8. No `PurchaseOrder` domain entity exists — POs are either an attached document on an existing order or a bulk-import workbook that becomes a draft cart.
- **Reorder**: explicitly unsupported for B2B orders (§11.3) — a deliberate scope limitation, not a bug, since a B2B reorder would need to reconstruct company/site/contract-price context that the current `createReorderCart` path doesn't carry.

### 14.8 Company-scoped authorization — summary
Every B2B mutation flows through `B2bAuthorizerImpl` (§8.7); every B2B service documents and follows a specific row-lock order relative to the company lock, collectively establishing one global lock hierarchy: `COMPANIES → {ORDERS, RFQS, PO_IMPORTS, PO_ATTACHMENTS, APPROVAL_REQUESTS, COMPANY_SITES, COMPANY_MEMBERS} → SLOT_COUNTERS` (with RFQ mutations as the one documented exception locking RFQ-row-first, deliberately, to avoid a deadlock cycle with the more common company-first order).

---

## 15. Background Jobs and Schedulers

No ShedLock/Quartz/distributed-lock library is used anywhere. Multi-instance safety for every scheduler below rests entirely on database-level primitives: row locks (`findByIdForUpdate`), CAS conditional UPDATEs, native upserts (`INSERT...ON CONFLICT`), or unique constraints caught as `DataIntegrityViolationException`. This is stated explicitly in most scheduler Javadocs, not merely an emergent property.

| Scheduler | Trigger | Purpose | Tx boundary | Locking/claim | Failure handling | Tables |
|---|---|---|---|---|---|---|
| `AccountDeletionSweeper` | cron `0 30 2 * * *` (nightly 02:30) | DPDP account deletion, per-table classify (§11.16) | No single tx; per-table isolated ops | `findDue(now, batch=50)`, unlocked reads, per-request/per-table try/catch | Any table failure keeps the request PENDING for full retry next sweep; every delete idempotent by construction | delete_requests, addresses, carts+items, coupon_redemptions, wishlist_entries, notify_me_subscriptions, search_queries, devices, login_events, notification_logs, delivery_slot_locks, support_tickets(+messages, conditional), users (anonymize) |
| `ApprovalEscalationJob` | fixedDelay 60s (configurable) | Delegates to `ApprovalEscalationService.escalateDue()` | Delegated (service `REQUIRES_NEW` per-request) | `findByIdForUpdate` + re-check `escalationDueAt` in the service | Per-request try/catch | approval_requests, approval_actions |
| `CartAbandonmentJob` | fixedDelay 5min | Notifies abandoned carts | Delegated, service `@Transactional` | None (per-cart try/catch) | Skips guests/phoneless users (stated permanent gap); relies on notification cooldown for suppression | carts, notification_logs |
| `CatalogOutboxRelay` | fixedDelay 5s | Relays outbox rows to RabbitMQ | None; per-event try/catch | `findPendingForRelay(maxAttempts=5, batch=50)` | Bounded retry, terminal FAILED at cap ("poison rows never block siblings") | catalog_outbox_events |
| `CatalogReindexer` | cron `0 0 2 * * *` (nightly, dev override `*/30 * * * * *`) | Blue-green full ES reindex + outbox-drift reconciliation | None (long-running, external ES calls) | Alias swap is the atomicity boundary | Old-index delete is best-effort (a documented past production incident: a failed-delete leak once filled a cluster's shard cap) | products, categories (read); catalog_outbox_events (mark-processed) |
| `DeliverySlotGenerator` | cron `0 5 0 * * *` (nightly 00:05) | Pre-creates rolling-window slot counters | `@Transactional` | `insertIfNotExists` (`INSERT...ON CONFLICT DO NOTHING`) | Fully idempotent, no-op on re-run | delivery_slot_counters |
| `IdempotencyPurgeScheduler` | cron `0 15 4 * * *` (nightly 04:15) | Deletes expired idempotency keys | None (bulk delete) | None needed — correctness enforced on the read path (window filter), a missed purge just degrades to table growth | N/A | idempotency_keys |
| `InvoiceDlqWorker` | `@RabbitListener` (DLQ) + cron `0 0 */2 * * *` (every 2h backstop) | DLQ retry + backstop sweep for stuck invoices | Delegates to `InvoiceGenerationService` (3-phase claim/render/commit) | Claim-based row lock + attempt count + 15min staleness | Per-invoice try/catch | invoices |
| `InvoiceGenerationScheduler` | fixedDelay 60s | Primary invoice-generation heartbeat | Delegates | Claim-based | Escalates to DLQ after 3 attempts, terminal FAILED at 6 total | invoices; publishes to DLX |
| `NotificationSweeper` | fixedDelay 60s (self-heal) + cron `0 0 3 * * *` (nightly cleanup) | Re-enqueues stuck PENDING notifications; deletes old terminal logs (30 days, hardcoded) | None; per-row try/catch | None (re-enqueue tolerates at-least-once duplicates, accepted tradeoff) | Per-row try/catch | notification_logs |
| `PiiBackfillSweeper` | fixedDelay 60s | Backfills AES-256-GCM encryption + HMAC blind indexes for rows written before encryption was introduced | None (delegates to repository transactions) | Raw `JdbcTemplate` batch selects (100/table, up to 50 batches/tick) | Latches off (`volatile boolean completed`) once a full pass finds nothing left — self-verifying completion | users, addresses, notification_logs |
| `RfqExpirySweeper` | fixedDelay 60s | Expires OPEN RFQs past `expiresAt` | None (single bulk conditional UPDATE) | The UPDATE's own row-level atomicity is the entire safety mechanism | Naturally idempotent | rfqs |
| `StaleOrderSweepJob` | **fixedDelay 60000 (hardcoded, only non-externalized interval in the package)** | Cancels stale PAYMENT_PENDING orders + expires stale slot locks | Delegates; `REQUIRES_NEW` self-injection for isolated cancel/release transactions (§11.9) | Row lock per order/lock; gateway reconciliation consulted before cancelling | Per-order/per-lock try/catch; failed lock release doesn't un-cancel the order | orders, payments, delivery_slot_locks, delivery_slot_counters, payment_reconciliations |
| `StatementScheduler` | fixedDelay 5min | Generate-due + recover-stuck + email-sweep heartbeat | Delegates entirely (§11.13) | Company-row lock + UNIQUE-constraint backstop | Per-company/per-statement try/catch | statements, statement_sequences, S3, email |

Queue-side consumers (not `@Scheduled` but message-driven, documented in §16): `OtpDispatchListener`, `NotificationDispatchListener` (+ its DLQ handler), `CatalogProductChangedListener`, `CatalogIndexedConfirmationListener`.


## 16. External Integrations

### 16.1 Pattern-level finding

Of the 13 adapters in `infra.external`/`infra.gateway`, only **2** have genuine production wiring: `GoogleTokenVerifier` (Google Sign-In) and `SmtpEmailSender` (SMTP email). Every other external capability is a `@Profile("!prod")` dev/logging stub with **no production counterpart in the repository**.

| Service | Port | Adapter | Profile | Status |
|---|---|---|---|---|
| Google Sign-In | `GoogleIdentityGateway` | `GoogleTokenVerifier` | always active | **Real** — `com.google.api.client.GoogleIdTokenVerifier` against Google's public keys, audience-restricted to `google.client-id`, no trust caching (re-verifies every call) |
| SMTP email | `EmailSender` | `SmtpEmailSender` | `prod` | **Real** — Spring `JavaMailSender`, supports attachments (statement email delivery); `LoggingEmailSender` (`!prod`) is the dev fallback |
| Payment gateway | `PaymentGateway` | `DummyPaymentGatewayAdapter` | `!prod` only | **Stub only** — see §12.1. No prod adapter; `prod` boot fails |
| Masked call proxy | `CallProxyGateway` | `DummyCallProxyGatewayAdapter` | `!prod` only | **Stub only** — no real telephony provider; smaller blast radius (not on the money path) |
| Geocoding | `GeocodingGateway` | `GoogleMapsGeocodingAdapter` | `!prod` | **Stub** — always returns a fixed coordinate (21.1458, 79.0882) regardless of input; no real Google Maps API call exists despite the class name |
| Address serviceability | `ServiceabilityGateway` | `StubServiceabilityGateway` | `!prod` | **Stub** — always returns `true` |
| OTP SMS delivery | `OtpSender` | `SmsOtpSender` | `!prod` | **Stub** — logs the OTP instead of sending; no `@Profile("prod")` counterpart exists |
| SMS notifications (general) | `SmsNotificationSender` | `LoggingSmsNotificationSender` | `!prod` | **Stub only** |
| WhatsApp notifications | `WhatsAppNotificationSender` | `LoggingWhatsAppNotificationSender` | `!prod` | **Stub only** |
| Push notifications | `PushNotificationSender` | `LoggingPushNotificationSender` | `!prod` | **Stub only** |
| Image search | `ImageSearchProvider` | `StubImageSearchProvider` | not profile-restricted | Always empty results — signals "no real vendor expected imminently even in prod" |
| Support-chat intent classification | `IntentClassifier` | `StubIntentClassifier` | `!prod` | Fixed confidence 0.2, `"UNKNOWN"` — always below the 0.6 auto-resolve threshold, so `/support/chat` always escalates by design of the stub |

**Consequence**: deploying with `spring.profiles.active=prod` will fail to boot wherever `PaymentGateway` or `CallProxyGateway` are `@Autowired` as required beans (unless a real adapter is added), and will silently run without real OTP SMS, push, WhatsApp, geocoding, or serviceability behavior for every other stub unless a matching `@Profile("prod")` implementation is added before deployment.

### 16.2 Messaging — RabbitMQ (`infra.messaging` + `infra.consumer` + `infra.config`)

Wire format: a single `Jackson2JsonMessageConverter` (JSON, not Java serialization) trusting package `com.builddash.backend`, applied to every queue.

| Config class | Main queue(s) | DLX/DLQ | Notes |
|---|---|---|---|
| `OtpQueueConfig` | `otp.dispatch` | `otp.dlx` / `otp.dispatch.dlq` | 3 broker retries (`RejectAndDontRequeueRecoverer`) before dead-lettering. Redeclaring the queue with new DLX args on an existing broker without them fails `PRECONDITION_FAILED` — requires manual queue deletion on upgrade |
| `CatalogQueueConfig` | `catalog.product.changed` (has DLQ) + `catalog.product.indexed` (confirmation, no DLQ) | `catalog.product.changed.dlx` / `.dlq` | Existed "from day one" — a silently-lost catalog-changed event means a product invisibly never reaches search. Messages carry `x-outbox-event-id` correlating back to the source outbox row. Confirmation-queue message loss is tolerated (caught later by nightly reindex reconciliation) |
| `NotificationQueueConfig` | `notification.push`, `notification.sms`, `notification.whatsapp` | `notification.dlx` / `notification.dlq` (shared) | Point-to-point default exchange; one shared DLQ since failure handling is identical across channels |
| `InvoiceQueueConfig` | (DLX/DLQ only: `invoice.generation.dlx`/`.dlq`) | — | The main generation trigger is scheduler-driven (§15), not queue-driven; only the DLQ side is a queue |

**Publishers**: `RabbitCatalogEventPublisher.publishProductChanged` uses **publisher confirms** (`waitForConfirms(5000ms)`), returning `false` (not throwing) on failure/timeout so the outbox relay can decide retry-vs-mark-published. `RabbitOtpDispatchQueue`/`RabbitNotificationDispatchQueue` use plain `convertAndSend` (no confirms).

**Consumers**: `OtpDispatchListener` → `OtpSender` port. `NotificationDispatchListener` → one method per channel + a DLQ handler marking the log row FAILED once broker retries are exhausted (a sender exception is allowed to propagate on purpose, so broker-level retry can engage). `CatalogProductChangedListener` → reads the raw AMQP body directly (matching how the publisher sends it), upserts into Elasticsearch, republishes a confirmation. `CatalogIndexedConfirmationListener` → marks the outbox row PROCESSED.

### 16.3 Elasticsearch (`infra.search`) — see §11.15 for the workflow trace.

Write path uses **external versioning** (`VersionType.External`, version = payload's `updatedAtEpochMillis`) so stale/out-of-order writes lose silently (409 caught, logged at debug) — last-writer-wins by timestamp. Admin/reindex path implements a **blue-green** pattern: timestamped index creation with a custom `autocomplete_analyzer` (edge-ngram 2–15 grams) plus a Hindi sub-field and a schema-free `flattened` attributes field, then an atomic alias swap. No `@Profile` restriction — Elasticsearch is always-required infrastructure.

### 16.4 Object storage — S3 (`infra.storage`)

`S3ObjectStorageAdapter`, no `@Profile` (always active). Config: `storage.s3.{bucket,region,endpoint,access-key,secret-key,path-style-access}`. `@PostConstruct` fails closed if access/secret key blank. 15s per-attempt / 30s total API timeout. `endpoint` set → MinIO/self-hosted mode. Operations: `store` (PutObject), `signedUrl` (presigned GET, 15min default TTL), `get` (fully-buffered GetObject, used by statement-email retry), `delete` (best-effort, missing key is not an error — supports the DPDP deletion sweep).

### 16.5 Email — SMTP

`spring-boot-starter-mail`, `spring.mail.properties.mail.smtp.{connectiontimeout,timeout,writetimeout}` all 5–10s. `SmtpEmailSender` (`prod` profile) supports attachments (statement PDF/XLSX delivery). `LoggingEmailSender` (`!prod`) is a no-op logger.

### 16.6 PDF / XLSX generation

**PDF** (`infra.pdf`, OpenPDF 2.0.3, the LGPL/MPL fork of iText 2): `OpenPdfInvoiceRenderer` (GST tax invoice — metadata, 7-column line-item table, totals; all monetary values via `BigDecimal.toPlainString()`, no float formatting; null-safe throughout). `OpenPdfStatementRenderer` (B2B monthly statement, parallel structure).

**XLSX** (`infra.excel`, Apache POI 5.3.0): `SxssfStatementWorkbookWriter` — **streaming** (`SXSSFWorkbook`, temp-file-compressed), pages through order/line data via a keyset cursor, never fully materializing the workbook in memory (important for large B2B companies). Three sheets: Summary, Orders, Line Items; monetary cells written as plain strings to avoid float round-tripping. `PoWorkbookParserAdapter` — streaming SAX parse of uploaded PO XLSX (deliberately never loads untrusted files fully via `XSSFWorkbook`, a documented memory-exhaustion hardening decision).


## 17. Configuration and Environment

Source files: `src/main/resources/application.yaml` (base/prod defaults), `application-dev.yaml`, `application-test.yaml`. Active profile: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` — **dev is the default if unset**, meaning an unconfigured deployment does not fail safe into a production posture; the operator must explicitly set `SPRING_PROFILES_ACTIVE` for prod behavior (Swagger disabled by base default, nightly-only reindex cron, etc.).

### 17.1 Fail-closed secrets (no default; missing value fails Spring context startup)
| Property | Consumer | Env var |
|---|---|---|
| `security.jwt.secret` | `JwtProperties`→`JwtCodec` | `JWT_SECRET` |
| `security.pii.master-key` | `ConfigPiiKeyProvider` (also validates: must be valid base64 decoding to exactly 32 bytes) | `PII_MASTER_KEY` |
| `payment.webhook-secret` | `PaymentProperties` | `PAYMENT_WEBHOOK_SECRET` |
| `storage.s3.access-key` / `storage.s3.secret-key` | `S3ObjectStorageAdapter` (`@PostConstruct`) | `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` |

### 17.2 Soft-default secrets (NOT fail-closed — a residual hardening inconsistency)
| Property | Default | Env var |
|---|---|---|
| `spring.datasource.password` | `builddash` | `DB_PASSWORD` |
| `spring.rabbitmq.password` | `guest` | `RABBITMQ_PASSWORD` |
| `delivery.webhook-api-key` | empty string | `DELIVERY_WEBHOOK_API_KEY` |

### 17.3 Full configuration key reference

| Property | Purpose | Default | Profile |
|---|---|---|---|
| `server.port` | HTTP port | 8081 (`PORT`) | all |
| `spring.task.scheduling.pool.size` | Scheduled-task thread pool | 5 | all |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | Upload caps | 2MB / 3MB | all (same in prod & test) |
| `spring.datasource.url/username/password` | JDBC connection | see §10.1 | all |
| `spring.datasource.hikari.*` | Pool sizing/timeouts | max=20, min-idle=10, conn-timeout=20s, idle=300s, max-life=1800s | all |
| `spring.jpa.hibernate.ddl-auto` | Schema mode | `validate` (fixed) | all |
| `spring.jpa.open-in-view` | OSIV | `false` (fixed) | all |
| `spring.data.redis.host/port` | Redis connection | localhost:6379 | all |
| `spring.rabbitmq.host/port/username/password` | Broker connection | localhost:5672/guest/guest | all |
| `spring.rabbitmq.listener.simple.retry.max-attempts` | Consumer retry | 3 (fixed) | all |
| `spring.rabbitmq.listener.simple.auto-startup` | Listener auto-start | `false` | test only |
| `spring.elasticsearch.uris` | ES connection | http://localhost:9200 | all |
| `spring.flyway.enabled/locations` | Migration config | enabled, `classpath:db/migration/{vendor}` | all |
| `security.jwt.issuer` | JWT `iss` claim | `builddash-backend` | all |
| `security.jwt.access-token-ttl-minutes` | Access TTL | 15 | all |
| `security.jwt.refresh-token-ttl-days` | Refresh TTL | 30 | all |
| `security.jwt.guest-token-ttl-hours` | Guest TTL | 24 | all |
| `security.rate-limit.rules.*` | Per-endpoint limits | see §8.6 | all |
| `account.deletion.grace-days` | DPDP grace period | 30 | all |
| `account.deletion.support-tickets` | Retain/hard-delete tickets | `HARD_DELETE` | all |
| `orders.idempotency-window-hours` | Order idempotency window | 24 | all |
| `delivery.modification-window-minutes` | Reschedule/cancel window | 15 | all |
| `approval.escalation.stage-hours` | Escalation stage duration | 24 | all |
| `approval.escalation.sweep-interval-ms` | Escalation poll interval | 60000 | all |
| `statement.scheduler.delay-ms` | Statement heartbeat | 300000 | all |
| `statement.generation.{max-attempts,stale-minutes,sweep-batch-limit}` | Statement generation tuning | 3 / 15 / 25 | all |
| `statement.email.{max-attempts,max-attachment-bytes,sweep-batch-limit}` | Statement email tuning | 5 / 10MiB / 25 | all |
| `notification.cart-abandonment.{after-minutes,poll-interval-ms,renotify-cooldown-hours}` | Cart abandonment | 60 / 300000 / 24 | all |
| `notification.sweep.{stuck-after-minutes,interval-ms}` | Notification self-heal | 10 / 60000 | all |
| `support.sla.*` | Per-category SLA hours | 24/4/24/4/48/48 | all |
| `google.client-id` | Google OAuth client ID | empty (soft-fail) | all |
| `otp.length/ttl-seconds/max-attempts/send-cooldown-seconds/rate-limit-per-hour` | OTP tuning | 6 / 300 / 3 / 60 / 5 | all (test: cooldown=0) |
| `storage.s3.bucket/region/endpoint/path-style-access` | S3 config | `builddash-invoices` / `ap-south-1` / MinIO localhost / `true` | all |
| `management.endpoints.web.exposure.include` | Actuator exposure | `health` only | all |
| `springdoc.swagger-ui.enabled` / `springdoc.api-docs.enabled` | Swagger exposure | `false` base; `true` dev (literal, not env-driven) | all |
| `catalog.reindex.cron` | Reindex schedule | `0 0 2 * * *` (dev: every 30s) | all |
| `logging.level.com.builddash.backend` | App log level | `DEBUG` | dev only |

### 17.4 Dev vs. test vs. prod distinctions

- **Dev**: Swagger forced on (literal `true`, ignores `SWAGGER_ENABLED`), DEBUG logging, fast reindex cron, `CatalogSeeder`/`CompanySeeder` active (`@Profile("dev")`), actuator health shows full details.
- **Test**: fixed insecure JWT/webhook secrets supplied, RabbitMQ listener auto-startup disabled, Redis/RabbitMQ/Elasticsearch actuator health indicators disabled (not part of the Testcontainers/jedis-mock setup), OTP cooldown zeroed, Hibernate statistics enabled.
- **Prod**: no profile-specific YAML block found beyond the base `application.yaml` defaults — "prod" is effectively "whatever `dev`/`test` don't override," relying on the fail-closed secrets (§17.1) and `!prod`-restricted stub adapters (§16.1) to force explicit configuration/implementation before a prod deployment can boot correctly.


## 18. Testing Architecture

**245 test source files** (`find src/test/java -name "*.java" | wc -l`; a file count, not a `@Test` method count) across `api/` (64 — 27 root-level cross-cutting + 26 controller + 11 order-controller), `application/` (92 — 59 impl + 3 listener + 14 scheduler + 4 GST-sequence + others), `infra/` (70), `domain/` (10, pure unit), `support/` (8 fixtures), root (1 context-load smoke test).

### 18.1 `AbstractIntegrationTest` — shared IT base class

`src/test/java/com/builddash/backend/support/AbstractIntegrationTest.java` (a 94-edge god-node in the codebase's own dependency graph — nearly every `*IT.java`/`*JpaIT.java` extends it). `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`. A static initializer starts three infra fakes/containers **once per JVM** (singleton-container pattern):
- **PostgreSQL**: real `PostgreSQLContainer<>("postgres:16-alpine")`, schema created by running the actual production Flyway migrations — deliberately no H2 mirror, to avoid dialect drift.
- **Redis**: `com.github.fppt.jedismock.RedisServer`, a pure-Java in-memory RESP server (not a container) — the real Lettuce client connects unmodified.
- **Object storage**: `MinIOContainer`, exercised through the real `storage.s3.*` properties.

Wired via `@DynamicPropertySource`, which also pins the test Hikari pool (`maximum-pool-size=10`, `minimum-idle=2`) and sets JVM system properties before context startup: a base64 32-byte `security.pii.master-key` (since the prod config has no default) and ten `security.rate-limit.rules.*.limit` overrides raised to `10000` (all ITs share the same `127.0.0.1` rate budget; `RateLimitIT` separately re-lowers these via its own higher-precedence `@DynamicPropertySource` to exercise real limits).

Shared helper `loginViaOtp(phone, deviceFingerprint)` drives a full OTP send/verify round trip via `MockMvc`, reading the "sent" OTP back from the injected `RecordingSmsGateway` test double. **No `@Transactional`/rollback-per-test** on the base class — tests run against real committed Postgres state, seeding/asserting durable rows rather than relying on transaction rollback for isolation.

### 18.2 The rollback-only nested-transaction trap (H2.3)

Proven by `StaleOrderSweepRollbackJpaIT`. The historical bug: `DeliverySlotService.releaseLock()` called from inside the sweep, under `REQUIRES_NEW`, was in a buggy earlier version actually sharing the sweep's own transaction — so a thrown exception from the release call marked that shared transaction rollback-only; the catch block appeared to handle it, but the eventual commit threw `UnexpectedRollbackException` and the just-computed order cancellation was lost too. Two `@Test` methods prove the fix on real Postgres: `releaseFailure_cannotRollBackCancellation()` (injects a `RuntimeException` via `@SpyBean`, asserts the order still ends `CANCELLED` despite the release failing) and `releaseSuccess_cancelsOrderReleasesLockReturnsCapacity()` (happy path, no capacity leak/double-release). This is the canonical "prove a transaction-boundary bug can't reproduce" pattern reused by `SweepVsWebhookJpaIT`, `RefundFinalizeVsWebhookRaceJpaIT`, `ApprovalResumeDoesNotReleaseUnrelatedLockJpaIT`.

### 18.3 Concurrency tests specifically

Real-Postgres, multi-threaded proofs exist for essentially every hardened concurrency mechanism documented in §19: `B2bDraftCartConcurrentCheckoutRaceJpaIT` (H2.1), `CartLineItemConcurrentUpsertRaceJpaIT` (H2.9), `DeliverySlotDoubleDecrementRaceJpaIT`, `DeliverySlotSwapDeadlockJpaIT` (H2.5), `ConcurrentOrderCreateIdempotencyJpaIT`, `RefundConcurrencyJpaIT`, `RefundFinalizeVsWebhookRaceJpaIT`, `RefundWebhookDuplicateSuccessRaceJpaIT`, `ReturnConcurrencyJpaIT`, `ReturnTransitionConcurrencyJpaIT`, `GstSequenceConcurrencyJpaIT`, `GstSequenceRollbackJpaIT`, `CompanyMembershipPromotionRaceJpaIT`, `ApprovalConcurrencyJpaIT`, `RfqConcurrencyJpaIT`, `PoConcurrencyJpaIT`, `StatementConcurrencyIT`, `StaleOrderSweepVsPaymentWebhookRaceJpaIT`/`SweepVsWebhookJpaIT`, `DeliverySlotCounterLeakOnAccountDeletionJpaIT`, `CatalogOutboxPoisonIsolationJpaIT`.

### 18.4 Security tests

`JwtTokenIssuerValidatorTest` (issue/parse/expiry/tamper/wrong-type rejection), `RateLimitFilterTest` (unit-level filter logic), `RateLimitIT` (full-stack, real configured limits, per-IP separation, window-expiry recovery), `SecretsFailClosedTest`/`ProductionConfigValidationTest` (proves missing secrets fail startup), `AdminAndVendorRoleSecurityIT`, `B2bMembershipClaimTest`, `WebSocketAuthChannelInterceptorTest`, `SearchImageAuthIT` (proves `/search/image` is GET-only-public-scoped, a specific documented fix), `PoWorkbookParserSecurityTest` (malicious-upload hardening), `PayloadValidationSizeLimitsTest`. The dominant authorization-testing pattern is a systematic `*AuthorizationMatrixIT` per subsystem (Approval, Company, CompanyPermission, PO, RFQ, Statement) enumerating role × resource-ownership combinations rather than one path at a time.

### 18.5 Test coverage map by subsystem (verifiable class presence only — no invented percentages)

| Subsystem | Representative test classes | Test types |
|---|---|---|
| Auth (OTP/JWT/device/Google) | `AuthControllerIT`, `OtpSendServiceTest`, `OtpVerificationServiceTest`, `JwtTokenIssuerValidatorTest`, `DeviceControllerIT`, `LoginEventIpCaptureIT`, `RedisOtpRateLimiterTest` | Unit + IT + security |
| Cart / checkout | `CartControllerIT`, `CartServiceImplTest`, `CartPricingCalculatorTest`, `CartLineItemConcurrentUpsertRaceJpaIT`, `GuestCartMergeIT`, `CheckoutControllerIT`, `B2bDraftCartConcurrentCheckoutRaceJpaIT` | Unit + IT + concurrency |
| Orders | `OrderControllerIT`, `OrderServiceImplTest`, `ConcurrentOrderCreateIdempotencyJpaIT`, `OrderConfirmedInvoiceAtomicityJpaIT`, `StaleOrderSweepServiceImplTest`/`RollbackJpaIT`/`VsPaymentWebhookRace` | Unit + IT + concurrency |
| Payments | `PaymentWebhookControllerIT`, `PaymentWebhookEdgeCasesIT`, `PaymentReconciliationJpaIT`, `DummyPaymentGatewayAdapterTest` | Unit + IT — gateway itself is a stub |
| Refunds | `RefundServiceImplTest`, `RefundGatewayAmbiguousVsRejectedTest`, `RefundConcurrencyJpaIT`, `RefundFinalizeVsWebhookRaceJpaIT` | Unit + IT + concurrency |
| Returns/QC | `ReturnControllerIT`, `ReturnConcurrencyJpaIT`, `ReturnMultiRejectedResubmitJpaIT`, `ReturnRefundGstNoteEndToEndIT` | Unit + IT + concurrency + state machine |
| Invoice / GST | `InvoiceControllerIT`, `InvoiceGstSnapshotIntegrityJpaIT`, `InvoiceThreePhaseGenerationTest`, `GstSequenceConcurrencyJpaIT`/`IntegrityJpaIT`/`RollbackJpaIT`/`FiscalYearRolloverJpaIT`, `FinancialCheckConstraintsAndRetentionJpaIT` | Unit + IT + concurrency (heaviest recent focus, per H9/H10) |
| B2B company/RBAC | `CompanyMembershipServiceImplTest`/`PromotionRaceJpaIT`, `CompanyPermissionMatrixIT`, `SuspendedCompanyMatrixIT`, `B2bAuthorizerTest` | Unit + IT + security matrix + concurrency |
| Approvals | `ApprovalServiceImplTest`, `ApprovalGateServiceImplTest`, `ApprovalConcurrencyJpaIT`, `ApprovalAuthorizationMatrixIT`, `ApprovalResumeDoesNotReleaseUnrelatedLockJpaIT` | Unit + IT + concurrency + security |
| RFQ | `RfqServiceImplTest`, `RfqLifecycleJpaIT`, `RfqConcurrencyJpaIT`, `RfqQuotePricePreservationJpaIT`, `RfqExpirySweeperTest` | Unit + IT + concurrency + security |
| Purchase Orders | `PoAttachmentServiceImplTest`/`LifecycleIT`, `PoImportServiceImplTest`/`LifecycleIT`, `PoConcurrencyJpaIT`, `PoWorkbookParserSecurityTest` | Unit + IT + concurrency + security |
| Monthly statements | `StatementGenerationServiceImplTest`, `StatementConcurrencyIT`, `StatementLargeScaleIT`, `SxssfStatementWorkbookWriterTest` | Unit + IT + concurrency + security |
| Delivery slots | `DeliverySlotServiceImplTest`, `DeliverySlotDoubleDecrementRaceJpaIT`, `DeliverySlotSwapDeadlockJpaIT`, `DeliverySlotCounterLeakOnAccountDeletionJpaIT` | Unit + IT + concurrency |
| Notifications | `NotificationServiceImplTest`, `NotificationTriggerListenerTest`/`DispatchIT`, `NotificationSweeperTest`/`SweepJpaIT` | Unit + IT |
| Search / catalog outbox | `CatalogOutboxRelayJpaIT`/`PoisonIsolationJpaIT`, `ElasticsearchBulkReindexJpaIT`, `SearchImageAuthIT` | Unit + IT + concurrency |
| Account deletion / PII | `AccountDeletionJpaIT`/`SweeperTest`, `PiiBackfillJpaIT`/`SweeperTest`, `PiiCipherAndIndexTest`, `PiiKeyFailClosedContextTest`, `UserDataExportJpaIT` | Unit + IT + concurrency |
| Q&A/reviews/wishlist/notify-me | `QnaControllerIT`, `ReviewControllerIT`, `WishlistControllerIT`, `NotifyMeControllerIT` | Unit + IT |
| Support | `SupportTicketControllerIT`, `SupportChatControllerIT`, `SupportTicketTransitionTest` | Unit + IT + state machine |
| **Thinner/no dedicated coverage** | Vendor-admin: only `VendorAdminServiceImplTest` (single class). No standalone coupon test class — coupon logic tested only through `CartCouponProRataAllocationTest`. | — |

### 18.6 Running the tests

`useJUnitPlatform()` is explicitly declared in `build.gradle`'s `test` task (confirmed by direct read), alongside `systemProperty 'api.version', '1.43'` (a Testcontainers/Docker-Engine API-version-negotiation workaround for modern Docker installs). Standard command: `./gradlew test`. **Docker is required** for every `*IT.java`/`*JpaIT.java` (real `PostgreSQLContainer` + `MinIOContainer`); pure unit tests (`domain/*`, most `application/impl/*Test.java`) do not require Docker. No separate Gradle source set for integration tests — `*IT.java` and `*Test.java` coexist under the single `test` task, distinguished only by naming convention.

---

## 19. Concurrency and Transaction Safety

**No entity anywhere declares `@Version`** — this codebase does not use JPA optimistic locking at all. Every concurrency-safety mechanism is one of: a pessimistic `SELECT...FOR UPDATE` lock, a CAS-style conditional `UPDATE...WHERE`, a native upsert (`INSERT...ON CONFLICT`), or a unique/exclusion constraint caught as `DataIntegrityViolationException` and translated to a domain exception. This is a deliberate, consistent architectural choice verified across the entire persistence layer, not an inconsistency between spots.

### 19.1 Mechanism catalogue

| Mechanism | Where | How |
|---|---|---|
| CAS one-time cart consumption | `CartRepositoryAdapter.claimForCheckout` | `UPDATE carts SET consumed_at=now() WHERE id=? AND consumed_at IS NULL`; caller checks `rowsUpdated==1` |
| Atomic upsert | `CartLineItemJpaRepository.upsert` | Native `INSERT...ON CONFLICT...DO UPDATE`; adapter re-syncs the JPA session's in-memory collection manually afterward |
| CAS slot-lock state machine | `DeliverySlotLockJpaRepository.tryTransitionStatus` | `UPDATE...SET status=:to WHERE id=:id AND status=:from`; no exception on lost race — caller must interpret a 0 row-count as "someone else won" |
| Pessimistic lock: slot capacity | `DeliverySlotCounterRepositoryAdapter.findBySlotIdAndSlotDateForUpdate` | `@Lock(PESSIMISTIC_WRITE)`; backstopped by `chk_slot_counter_capacity` DB CHECK even if application logic is bypassed |
| First-write race on counter creation | `insertIfNotExists` | Native conditional insert avoids two concurrent bookings colliding on `uq_slot_counter_slot_date` with a raw exception |
| Native-insert idempotency dedup | `IdempotencyKeyRepositoryAdapter` (raw `JdbcTemplate`, not JPA) | Forces a real unique-constraint violation on double-submit instead of a silent `merge()` upsert |
| Overlap window: app check + DB exclusion, `saveAndFlush` | `ContractPriceRepositoryAdapter`/`CompanyContractPriceRepositoryAdapter` | In-memory check gives a clean exception on the non-racing path; `saveAndFlush` (not plain `save`) forces the GiST exclusion constraint to fire inside the method so the `catch` block can still translate it |
| Pessimistic lock: GST/statement sequence | `GstSequenceJpaRepository`/`StatementSequenceJpaRepository` (fallback path only — primary path is the native upsert, §11.12/§13.3) | `@Lock(PESSIMISTIC_WRITE)` on the composite-PK row |
| Pessimistic lock: invoice/statement claim | `InvoiceJpaRepository`/`StatementJpaRepository` findById | Prevents two scheduler runs (or scheduler + manual retry) double-processing the same document |
| Pessimistic lock: company/member/site/approval/RFQ/PO mutation | Respective `*JpaRepository` findById/findByCompanyId | Serializes concurrent admin/state-machine actions per aggregate |
| Two-phase durable claim | `PoAttachmentRepositoryAdapter.finalizeStored` | PENDING committed before the file write (outside any tx); conditional UPDATE only succeeds if still PENDING |
| Bulk claim-and-flip | `RfqJpaRepository.expireOpenBefore` | Single conditional bulk UPDATE, no per-row locking needed (no read-modify-write gap) |
| Partial-unique-index-as-concurrency-control | `returns`, `delete_requests`, `rfq_quotes`, `approval_actions` | No app-level lock at all — the losing side of a race gets a `DataIntegrityViolationException`, translated to the domain 409 |

### 19.2 Lock ordering (deadlock avoidance)

Two documented canonical orders coexist by design, chosen per-aggregate to avoid cross-service deadlock cycles:
- **Company-first**: `COMPANIES → {ORDERS, APPROVAL_REQUESTS, COMPANY_SITES, COMPANY_MEMBERS, PO_IMPORTS, PO_ATTACHMENTS} → SLOT_COUNTERS` — used by `B2bAuthorizerImpl`-driven flows (checkout, approval, membership, site).
- **RFQ-first**: `RFQS → COMPANIES` — used only by `RfqServiceImpl`, deliberately reversed from the company-first order because RFQ mutations are keyed by RFQ id first; documented explicitly as chosen to avoid a deadlock cycle with the more common ordering.
- **Delivery-slot swap**: `lockCountersCanonical()` compares `slotId` then `slotDate` to give a total order across the two counters involved in a slot swap, regardless of which direction the swap runs — proven deadlock-free by `DeliverySlotSwapDeadlockJpaIT`.
- **Refund/return webhook**: `RETURN → REFUND`, the same order used by both the webhook handler and the refund-service finalize phase, so a concurrent webhook delivery and a finalize call always serialize the same way.
- **Payment webhook vs. stale-order sweep**: both take `orderRepository.findByIdForUpdate(orderId)` as their first act — whichever commits first wins deterministically; the loser re-reads post-commit state (H1.5).

```mermaid
sequenceDiagram
    participant Webhook as PaymentWebhookServiceImpl
    participant Sweep as StaleOrderSweepServiceImpl
    participant DB as orders row

    par concurrent
        Webhook->>DB: findByIdForUpdate(orderId)
    and
        Sweep->>DB: findByIdForUpdate(orderId)
    end
    Note over DB: Postgres serializes — one wins the lock first
    DB-->>Webhook: lock acquired (example: webhook wins)
    Webhook->>DB: confirm(), commit
    DB-->>Sweep: lock acquired after commit
    Sweep->>Sweep: re-read order — now CONFIRMED, not stale — skip cancel
```

### 19.3 The REQUIRES_NEW self-injection pattern (rollback-trap avoidance)

`StaleOrderSweepServiceImpl` injects itself (`@Lazy @Autowired self`) specifically so that `sweepOrder()`/`releaseLockForOrder()`/`sweepExpiredLock()` are invoked *through the Spring proxy* rather than as a direct `this.` call — a direct call would silently bypass the `@Transactional(REQUIRES_NEW)` annotation (a well-known Spring AOP proxy limitation) and reintroduce the rollback-trap bug proven fixed by `StaleOrderSweepRollbackJpaIT` (§18.2).

### 19.4 Idempotency keys / duplicate-request handling — see §12.2.

### 19.5 Stale-claim handling
Every claim-based scheduler (invoice generation, statement generation) treats a fresh (<15min-old) `GENERATING`/`PENDING` claim as another worker's active ownership and skips it without bumping attempt count; only a *stale* claim (older than the threshold) is reclaimed — preventing two concurrent scheduler instances from double-processing the same document while still recovering from a worker that crashed mid-claim.

### 19.6 Domain-model gap: unguarded Payment/Refund/Invoice status mutations
`Payment.markSuccess()`/`markFailed()`, `Refund.markSuccess()`/`markFailed()`, and `Invoice`'s status-transition methods do **not** guard their *from* state at the domain-record level (unlike `Order`/`Return`, whose transition methods throw on an invalid *from* state). All idempotency/already-terminal protection for these three aggregates is therefore an application-layer responsibility, verified to be correctly enforced by the specific webhook/scheduler code paths documented in §11.9/§11.10/§11.12 (row lock + post-lock status re-check) — but this is a code-organization inconsistency worth flagging: a future caller of `Payment.markSuccess()` outside the currently-audited call sites would get no protection from the domain model itself.


## 20. Security Model

### 20.1 Secret Management
- **Fail-closed secrets:** The application does not provide defaults for critical cryptographic secrets. `JWT_SECRET`, `PII_MASTER_KEY`, and `PAYMENT_WEBHOOK_SECRET` must be explicitly provided in the environment. The application will fail to boot if they are missing.
- **Environment variables:** Configured through standard Spring Boot `${ENV_VAR}` property resolution. `.env.example` provides templates for developer workstations, but deliberately excludes production secrets.

### 20.2 PII Encryption (Data Protection)
- **AES-256-GCM:** Personally Identifiable Information (PII) is encrypted at rest using AES-256-GCM via `PiiCipher` and `PiiCryptoHolder`.
- **HMAC Blind Indexing:** To allow searching on encrypted PII (e.g., finding a user by phone number or email) without decrypting the entire database, the system uses an `HmacIndex`. This computes a deterministic HMAC-SHA256 of the plaintext, which is stored in an index column and used for exact-match database lookups.
- **Data Export & Deletion:** The system supports DPDP-compliant data export and account deletion workflows, implemented via `UserDataExporter` and `AccountDeletionSweeper`.

### 20.3 Input Validation and Hardening
- **Jakarta Bean Validation:** All inbound request DTOs are validated using `@Valid` with strict constraints.
- **Upload Validation:** `servlet.multipart` enforces hard limits (2MB max file size, 3MB max request size) to prevent resource exhaustion, supplementing application-layer rules (e.g., PO Excel uploads, return photo uploads).
- **Rate Limiting:** IP-based rate limiting via Redis (`RedisRateLimiter`) protects sensitive endpoints (OTP dispatch, Google Sign-in, review/question creation, support tickets, return creation, and search) against abuse and scraping. Configured in `application.yaml` under `security.rate-limit.rules`.

### 20.4 API Abuse and Webhook Security
- **HMAC Signatures:** Payment and refund webhooks require HMAC signature validation against `PAYMENT_WEBHOOK_SECRET`.
- **Idempotency:** Replay attacks and network retries on webhooks are mitigated using database-level lock-and-check (stale claim recovery) and idempotency keys.
- **Swagger Exposure:** OpenAPI/Swagger UI is conditionally exposed via `springdoc.swagger-ui.enabled` and protected/disabled in production profiles.

---

## 21. Reliability and Failure Handling

### 21.1 Retry and Idempotency
- **Outbox Pattern:** The system uses a transactional outbox (`OutboxEvent`) for domain events requiring external dispatch (e.g., OTPs, notifications, search indexing). Events are committed in the same database transaction as the business entity, then polled and dispatched asynchronously.
- **RabbitMQ Retries:** Consumers (`infra.consumer`) rely on Spring AMQP's built-in retry mechanism (configured to 3 max attempts). Failed messages route to a Dead Letter Queue (DLQ).
- **Idempotency Keys:** Specific mutations (like native inserts in `IdempotencyKeyRepositoryAdapter`) enforce idempotency at the database constraint level, ensuring double-submissions yield a clean constraint violation rather than corrupting state.

### 21.2 Crash Recovery
- **Stale Claim Recovery:** Scheduled jobs like `InvoiceSnapshotBuilderImpl` and `StatementScheduler` claim work using a timestamped lock. If a worker crashes mid-processing, claims older than a defined threshold (e.g., 15 minutes) are deemed stale and picked up by another worker.
- **Orphan Detection:** The `StaleOrderSweepJob` routinely sweeps for orders stuck in `PAYMENT_PENDING` past their expiry window, verifies their status against the payment gateway to prevent race conditions (see §19.2), and safely cancels them, releasing tied-up delivery capacity.

### 21.3 External Fault Tolerance
- **Timeouts:** External connections (Postgres, RabbitMQ, Redis, Elasticsearch, SMTP) define explicit connection and read/write timeouts to prevent thread starvation.
- **Circuit Breaking:** Not explicitly implemented via libraries like Resilience4j. Instead, external failures bubble up as specific domain exceptions or trigger asynchronous retries via the outbox/DLQ flow.

---

## 22. Operational Model

### 22.1 Runtime Components
- **Single JVM:** A monolithic Spring Boot process runs the HTTP server, WebSocket server, background `@Scheduled` jobs, and RabbitMQ listeners.
- **Database:** PostgreSQL is the definitive source of truth and primary concurrency coordinator.
- **Redis:** Used exclusively for ephemeral state (OTP codes, rate limiting counters, Bloom filters).
- **RabbitMQ:** Used for asynchronous decoupling (notifications, search indexing).
- **Elasticsearch:** Read-optimized product catalog and autocomplete index.
- **S3 / MinIO:** Object storage for return photos, PO attachments, generated invoices, and statements.

### 22.2 Health and Observability
- **Health Checks:** Spring Boot Actuator exposes a `/actuator/health` endpoint for load balancers and container orchestrators.
- **Metrics:** Configured with `micrometer-registry-prometheus`. Actuator conditionally exposes the `/actuator/prometheus` endpoint in non-production or explicitly configured environments (`MANAGEMENT_ENDPOINTS_INCLUDE`). Custom application metrics are recorded via `ApplicationMetrics` for key events (e.g., `app.payment.webhooks`, `app.checkout.attempts`, `app.auth.failures`).
- **Logs:** Standard Spring Boot SLF4J/Logback stdout logging. `application-dev.yaml` defines `DEBUG` level for `com.builddash.backend`.

### 22.3 Scheduled Jobs
The `@Scheduled` thread pool is sized to 5 (`spring.task.scheduling.pool.size: 5`). Jobs include:
- `StaleOrderSweepJob` (every 60s)
- `RfqExpirySweeper` (every 60s)
- `StatementScheduler` (every 5m)
- `InvoiceSnapshotScheduler` (every 5m)
- `AccountDeletionSweeper` (nightly)

---

## 23. Deployment

### 23.1 Build and Packaging
- **Toolchain:** Gradle 9.7.1, Java 21.
- **Build Command:** `./gradlew build` (runs compilation and tests).
- **Artifact:** Executable Spring Boot "fat" JAR generated in `build/libs/`.

### 23.2 Infrastructure Prerequisites
Deploying this application requires external provisioning of:
1. PostgreSQL 16 database.
2. Redis 7 instance.
3. RabbitMQ 3 cluster/instance.
4. Elasticsearch 8.15 cluster.
5. S3 bucket (with IAM credentials) or MinIO server.
6. SMTP server credentials (for statement delivery).

### 23.3 Environment Variables
Production deployments must supply:
- `SPRING_PROFILES_ACTIVE=prod`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `ELASTICSEARCH_URIS`
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `S3_BUCKET`, `S3_REGION`, `S3_ENDPOINT`
- `JWT_SECRET`, `PII_MASTER_KEY`, `PAYMENT_WEBHOOK_SECRET`
- SMTP configurations (usually via `SPRING_MAIL_*` overrides)

### 23.4 Database Migrations
Flyway is enabled by default (`spring.flyway.enabled: true`). It runs automatically on application startup, applying migrations from `classpath:db/migration/postgresql`.

---

## 24. Developer Guide

### 24.1 Local Setup
1. **Prerequisites:** Java 21 JDK, Docker Desktop (or equivalent).
2. **Infrastructure:** Run `docker-compose up -d` to spin up local Postgres, Redis, RabbitMQ, Elasticsearch, and MinIO.
3. **Configuration:** Copy `.env.example` to `.env`. For local development, dummy values for secrets are acceptable if testing features that don't strictly require live external integration.
4. **Run Application:** `./gradlew bootRun` (defaults to `dev` profile).
5. **Run Tests:** `./gradlew test`. Testcontainers will spin up ephemeral Postgres and MinIO containers for integration tests.

### 24.2 Adding a Domain Feature
1. **Domain:** Define immutable records in `domain.model`. Add specific `domain.exception` classes. Define ports in `domain.port`.
2. **Persistence:** Add Flyway migration in `src/main/resources/db/migration/postgresql`. Create JPA entity and repository. Implement the outbound port in `infra.persistence.adapter`.
3. **Application:** Create interface in `application.service` and implementation in `application.impl`. Apply `@Transactional` where necessary, adhering to documented lock ordering (§19.2).
4. **API:** Create request/response DTOs, a mapper in `api.mapper`, and the REST controller.
5. **Tests:** Add unit tests for business logic and integration tests (extending a Testcontainers base class) for database/API boundaries.

---

## 25. Code Conventions

- **Hexagonal / Clean Architecture:** Strict dependency rule. `infra` depends on `domain` and `application`; `domain` depends on nothing.
- **Immutability:** Extensive use of Java `record` for DTOs, Domain Models, and Events.
- **Fail-Fast Exceptions:** Business logic throws specific `DomainException` subclasses immediately when rules are violated. `GlobalExceptionHandler` translates these to HTTP 400/409/422/404.
- **Pessimistic Concurrency:** Widespread use of `@Lock(PESSIMISTIC_WRITE)` and `findByIdForUpdate` over `@Version` optimistic locking.
- **Database-Driven Safety:** Preference for native upserts (`ON CONFLICT DO UPDATE`), `EXCLUDE USING gist`, and `CHECK` constraints to enforce invariants, backed by application-layer retry/catch mechanisms.
- **Test Nomenclature:** `*Test.java` for unit tests, `*IT.java` (and sometimes `*Test.java` invoking Testcontainers) for integration tests.

---

## 26. Architectural Decision Record Summary

1. **Pure Java 21 (No Kotlin):** Despite early project planning naming Kotlin, the codebase is entirely Java 21, leveraging records and pattern matching to achieve similar conciseness.
2. **Pessimistic > Optimistic Locking:** Chose explicit database row-locking and native `UPDATE ... WHERE` conditional statements for concurrency control to avoid `OptimisticLockException` retry loops in high-contention workflows (e.g., inventory, delivery slots, order state machines).
3. **Hexagonal Package Structure (Module-less):** Implemented Hexagonal Architecture via package conventions (`api`, `application`, `domain`, `infra`) in a single Gradle module rather than multi-module, balancing strictness with developer velocity.
4. **Fail-Closed Secrets:** Boot sequence is hard-gated on the presence of critical cryptographic secrets (`PII_MASTER_KEY`, `JWT_SECRET`) to prevent accidental insecure deployments.
5. **In-JVM Schedulers:** Opted for `@Scheduled` with DB-backed claim mechanisms instead of heavy job orchestrators (Quartz/ShedLock) to reduce infrastructure complexity.
6. **Outbox Pattern:** Ensured external side-effects (RabbitMQ, Search) are transactionally guaranteed with business state changes via `OutboxEvent` and async relay.

---

## 27. Production Readiness

**Verdict: NOT fully production-ready.** While the codebase demonstrates exceptional robustness in concurrency, transaction boundaries, and database integrity (H0-H10 hardening), it lacks final external integrations.

### 27.1 Implemented and Verified (Ready)
- Authentication (OTP/JWT) and Authorization (B2B RBAC).
- Core e-commerce flows (Catalog, Cart, Checkout, Order Lifecycle).
- B2B Workflows (RFQ, Quotes, Approvals, PO Import, Statements).
- Database schemas, migrations, constraints, and lock ordering.
- PII encryption and Data Deletion (DPDP compliance).
- Accounting logic, Invoice generation (PDF), and GST sequential numbering.

### 27.2 Requires External Provider Integration (Blockers)
- **Payment Gateway:** `DummyPaymentGatewayAdapter` must be replaced with a real integration (e.g., Razorpay, Stripe, PayU).
- **Notification Providers:** SMS, WhatsApp, and Push notification adapters are stubs (`DummySmsAdapter`, etc.).
- **Logistics/Maps:** Geocoding and address serviceability adapters are stubs.

### 27.3 Operationally Recommended
- Centralized structured logging (JSON format) for log aggregation.
- Grafana dashboards consuming the `/actuator/prometheus` endpoint.
- CI/CD pipeline definitions (GitHub Actions/GitLab CI).

---

## 28. Known Limitations and Residual Risks

| Issue | Impact | Mitigation / Status |
|---|---|---|
| **Stub Integrations** | Payments, SMS, and geocoding will not work in a real production environment. | **Requires Code Change:** Real outbound adapters must be implemented in `infra.external`/`infra.gateway`. |
| **Unguarded Domain Status Mutations** | `Payment.markSuccess()` and `Refund.markSuccess()` lack domain-level `from` state guards (§19.6). | **Mitigated in Application Layer:** The current webhook handlers enforce the guard via DB row locks, but future call sites might bypass it. Code refactor recommended. |
| **Stale Test Output File** | `test_output.log` (242KB) sitting at repo root. | Minor repo hygiene issue. |
| **In-Memory Job Coordination** | Schedulers rely purely on DB state to avoid collision across instances. | If DB load becomes an issue under heavy scale, a dedicated distributed locking library (ShedLock) may be necessary. |
| **Monolithic DB Schema** | All domains (B2B, B2C, Invoicing, Support) share the same PostgreSQL database. | Scaling out specific domains will require significant refactoring to microservices. |

---

## 29. Traceability Matrix

### Feature to Implementation
| Feature | Primary API / Controller | Core Service (`application.impl`) | Primary Entity | Outbound Integrations |
|---|---|---|---|---|
| **Checkout (B2C)** | `CheckoutController` | `OrderServiceImpl` | `OrderEntity`, `CartEntity` | Payments (Stub), Delivery Slots |
| **B2B Approvals** | `ApprovalController` | `ApprovalServiceImpl` | `ApprovalRequestEntity` | Notifications (Stub) |
| **RFQ & Quotes** | `RfqController` | `RfqServiceImpl` | `RfqEntity`, `RfqQuoteEntity` | Notifications (Stub) |
| **GST Invoicing** | `InvoiceController` | `InvoiceSnapshotBuilderImpl` | `InvoiceEntity`, `GstSequenceEntity` | PDF Generation (OpenPDF), S3 |
| **B2B Statements** | `StatementController` | `StatementScheduler` | `StatementEntity` | PDF/XLSX, S3, SMTP (JavaMail) |
| **Returns & QC** | `ReturnController` | `ReturnServiceImpl` | `ReturnEntity` | S3 (Photo Uploads) |
| **Search/Catalog** | `SearchController` | `CatalogServiceImpl` | `ProductEntity` | Elasticsearch, RabbitMQ |

### Security Control Mapping
| Control | Implementation Location | Verified By |
|---|---|---|
| **Authentication** | `JwtAuthenticationFilter`, `GoogleTokenVerifier` | `SecurityConfig` tests |
| **B2B Authorization** | `B2bAuthorizerImpl`, Method-level checks | `B2bAuthorizerImplTest` |
| **PII Encryption** | `PiiCipher`, `HmacIndex`, `@Converter` | `PiiCryptoHolderTest` |
| **Rate Limiting** | `RateLimitFilter`, `RedisRateLimiter` | Integration Tests |
| **Webhook Auth** | `PaymentWebhookServiceImpl` HMAC checks | `PaymentWebhookServiceImplTest` |

---

## 30. Glossary

- **B2B (Business-to-Business):** Features for corporate clients, including company accounts, sites, roles, and purchase orders (PO).
- **B2C (Business-to-Consumer):** Direct consumer retail features.
- **DPDP:** Digital Personal Data Protection (India compliance). Enforced via PII encryption, data export, and account deletion.
- **GST:** Goods and Services Tax (India). The system maintains strict sequential numbering for invoices and captures tax snapshots per order line.
- **HSN:** Harmonized System of Nomenclature. Codes attached to products determining GST rates.
- **PO (Purchase Order):** B2B order request, can be uploaded in bulk (XLSX).
- **RFQ (Request for Quote):** B2B workflow where a company requests pricing for items, vendors submit quotes, and the company converts the winning quote to an order.
- **QC (Quality Control):** Gating step in the Return workflow before refunds are issued.

---

## 31. Quick Reference

### Important Directories
- **Source Code:** `src/main/java/com/builddash/backend/`
- **JPA Entities:** `.../infra/persistence/entity/`
- **Domain Models:** `.../domain/model/`
- **Application Services:** `.../application/impl/`
- **Database Migrations:** `src/main/resources/db/migration/postgresql/`

### Important Files
- `SecurityConfig.java`: Root HTTP security and authorization.
- `OrderServiceImpl.java`: Core checkout and order creation state machine.
- `DeliverySlotServiceImpl.java`: Complex lock-ordered concurrency control.
- `B2bAuthorizerImpl.java`: Company RBAC and permission enforcement.
- `application.yaml`: Core properties and connection defaults.

### Important Commands
- **Run Tests:** `./gradlew test`
- **Run App (Dev):** `./gradlew bootRun`
- **Build Artifact:** `./gradlew build`
- **Start Local Infra:** `docker-compose up -d`

### Major Scheduled Jobs
- `StaleOrderSweepJob` (60s)
- `StatementScheduler` (5m)
- `AccountDeletionSweeper` (Nightly)
