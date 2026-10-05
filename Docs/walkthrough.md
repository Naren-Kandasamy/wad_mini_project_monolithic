# Cumulative Project Walkthrough & Verification Journal

## Living Document Purpose
This walkthrough is the authoritative, phase-by-phase operational journal for the **Shopping Cart Modular Monolith** development. It is structured to monitor **3 parallel development tracks** (one per teammate on isolated git branches), followed by the **joint integration and hardening pass**.

Every track entry captures:
1. **Changes Done in That Track/Phase** (Primary focus: files created, modified, architectural integrations completed).
2. **Test Suite Statistics** (Pass, fail, warning, skipped metrics, and execution times).
3. **Bug Fix & Failed Test Resolution Log** (Root cause analysis and exact fixes applied for historical debugging knowledge).
4. **Technical Debt, Mocked & Stubbed Elements Ledger** (Explicitly registering all temporary shortcuts, mocks, stubs, and debts to ensure 100% elimination during the final pass).

---

## Documents Referred & System Context
When reviewing and executing changes across phases, all developers cross-reference these core architecture specifications in `Docs/`:
- **Master Architecture:** [shopping-cart-monolith-master-architecture.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/shopping-cart-monolith-master-architecture.md)
- **Group 1 (Core & Checkout):** [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md)
- **Group 2 (Security & Auth):** [architecture-monolith-grp2-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md)
- **Group 3 (Testing & Operations):** [architecture-monolith-grp3-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp3-ready.md)
- **Master Implementation Plan Template:** [implementation-plan.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/implementation-plan.md)
- **Standardized Stack Baseline:** Spring Boot 4.1.1 on Java 21 (LTS), Vue.js 3 SPA, MongoDB 7.x (Replica Set `rs0`), Keycloak 24+

---

## Master Track Progression Tracker

| Track / Stream | Owner & Scope | Branch | Status | Tests Run | Pass Rate | Unresolved Tech Debt |
|---|---|---|---|---|---|---|
| **Track 1** | Teammate 1: Core Domain & Tx Engine | `feature/track1-core-domain` | 🟢 Complete | 37 | 100% | 8 documented handoff debts |
| **Track 2** | Teammate 2: Security & Platform API | `feature/track2-security-platform` | 🟡 Ready to Begin | 0 | - | 0 |
| **Track 3** | Teammate 3: Infra, Vue 3 & E2E Suite | `feature/track3-infra-frontend-tests` | 🟡 Ready to Begin | 0 | - | 0 |
| **Joint Phase 4**| All Teammates: Merge, Smoke & Hardening | `main` | ⚪ Pending | 0 | - | - |

---

## Track 1: Core Domain & Transaction Engine (Teammate 1)
**Branch:** `feature/track1-core-domain`  
**Reference Document:** [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md)

### 1. Changes Done in Track 1
- **Backend Initialized (`backend/`):**
  - Standardized on Spring Boot 4.1.1 + Java 21 LTS with Maven wrapper (`./mvnw`).
  - Configured `spring.mongodb.uri` pointing to MongoDB 7 replica set `rs0` (`mongodb://127.0.0.1:27018/shopping_cart_test?replicaSet=rs0&directConnection=true`).
  - Registered `MongoTransactionManager` bean in `MongoConfig.java` to drive Spring `@Transactional` multi-document session boundaries.
- **Typed In-Process Contracts & Domain Models:**
  - `ProductApi.java` & `ProductSnapshot.java` (Java 21 immutable record).
  - `CartApi.java`, `CartItemSnapshot.java`, `CheckoutCart.java`.
  - `OrderApi.java`, `OrderSnapshot.java`, `CreateOrderCommand.java`.
  - Persistence entities: `ProductDocument.java` (collection `products`, unique `sku`), `CartDocument.java` (collection `carts`, unique `userId`, version counter), `OrderDocument.java` (collection `orders`, compound unique index on `userId + idempotencyKey`).
- **Domain Modules Implemented:**
  - **Product Module:** `ProductRepository`, `ProductService`, `ProductServiceImpl` (catalog retrieval, soft delete, active-only filtering), `ProductController` (`/api/products`).
  - **Cart Module:** `CartRepository`, `CartService`, `CartServiceImpl` (implements `CartApi`, atomic conditional update `{userId, expectedVersion}` incrementing version or throwing 409 Conflict), `CartController` (`/api/carts/me`).
  - **Order Module:** `OrderRepository`, `OrderService`, `OrderServiceImpl` (implements `OrderApi`, generates immutable snapshot orders strictly with status `CONFIRMED`), `OrderController` (`/api/orders`).
  - **Checkout Module:** `CheckoutService` (orchestrates atomic multi-document transaction: idempotency check -> load cart -> calculate total -> create order -> atomic cart version finalize -> commit), `CheckoutController` (`POST /api/orders/checkout`).
- **Cross-Cutting & Decoupling:**
  - `GlobalExceptionHandler.java`: maps domain exceptions to standard HTTP codes (404 Not Found, 409 Conflict for version mismatch/empty cart, 400 Bad Request, 500 Internal Error).
  - `SecurityContextPrincipalResolver.java`: resolves `Jwt.getSubject()` when authenticated, defaulting cleanly to `"user1"` for standalone offline domain test execution.

### 2. Test Suite Statistics (Track 1)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| Domain Unit Tests (Calculations, Snapshots, Availability) | 19 | 19 | 0 | 0 | 0 | ~2.5s |
| Web MVC Controller Tests (REST Contracts & Status Codes) | 11 | 11 | 0 | 0 | 0 | ~3.0s |
| Mongo Replica Set Persistence & Rollback Tests | 3 | 3 | 0 | 0 | 0 | ~10.2s |
| Cart Version Conflict & Abort Tests | 2 | 2 | 0 | 0 | 0 | ~10.4s |
| Idempotency Race Concurrency Tests | 1 | 1 | 0 | 0 | 0 | ~0.1s |
| Context Bootstrapping Test | 1 | 1 | 0 | 0 | 0 | ~1.1s |
| **TOTAL** | **37** | **37** | **0** | **0** | **0** | **~26.5s** |

### 3. Bug Fix & Failed Test Resolution Log (Track 1)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| BF-T1-01 | `ProductServiceImpl.java` | Compilation error on line 50: `method reference not expected here` | Inappropriate method reference `ProductResponse::fromDocument.apply(doc)`. | Replaced with standard static method call `ProductResponse.fromDocument(doc)`. |
| BF-T1-02 | Spring Boot 4 MongoDB Config | Tests defaulted to standalone MongoDB on `localhost:27017` despite `spring.data.mongodb.uri` in YAML. | In Spring Boot 4.0+, `spring.data.mongodb.uri` is deprecated/error and replaced by `spring.mongodb.uri`. | Updated `application.yml` and `application-test.yml` to specify `spring.mongodb.uri`. |
| BF-T1-03 | Web MVC Test Package Imports | `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` not found. | Spring Boot 4 relocated WebMvc test autoconfiguration to `org.springframework.boot.webmvc.test.autoconfigure.*`. | Updated import statements across all controller tests. |
| BF-T1-04 | `CartServiceImpl.java` | `CartNotFoundException` thrown instead of `CartEmptyException` when user had no cart document. | Missing cart document was treated as 404 rather than empty cart 409 conflict during checkout. | Updated `loadForCheckout` to treat missing cart as `CartEmptyException`. |
| BF-T1-05 | `CheckoutTransactionIntegrationTest` | Timestamp assertion mismatch between JVM `Instant.now()` and MongoDB BSON date. | JVM `Instant` has nanosecond precision; MongoDB BSON date stores millisecond precision. | Asserted equality using `toEpochMilli()`. |
| BF-T1-06 | `CartVersionConflictIntegrationTest` | Version conflict rollback test did not trigger conflict. | Artificially updating version in MongoDB before `processCheckout` caused `loadForCheckout` to load the newer version. | Used `@MockitoSpyBean` on `CartApi` to mutate cart version concurrently at the exact invocation instant of `finalizeCheckout`. |

### 4. Technical Debt, Mocks & Stubs Ledger (Track 1)

The following items were intentionally mocked, stubbed, hardcoded, or bypassed during Track 1 development to maintain track isolation and enable unblocked offline development. All items have explicit removal triggers and target tracks:

| Debt ID | Component & Exact Code Location | Nature of Debt (Mock/Stub/Hardcoding/Bypass) | Target Elimination Milestone | Handoff Owner & Action Required Upon Integration |
|---|---|---|---|---|
| **TD-T1-01** | `SecurityContextPrincipalResolver.java:14` | **Hardcoded Principal Fallback**: Returns hardcoded `"user1"` when `SecurityContextHolder` contains no active JWT authentication token. | Track 2 (Security) & Phase 4 | **Teammate 2**: Once Keycloak OAuth2 Resource Server filter chain is configured, delete or disable the `"user1"` fallback in production/joint profile so unauthenticated requests fail with 401 Unauthorized. |
| **TD-T1-02** | `ProductController.java` (`POST`, `PUT`, `DELETE`) & `OrderController.java` (`/admin/{orderId}`) | **Missing Method Security Annotations**: Endpoints lack `@PreAuthorize("hasRole('ADMIN')")` or `@PreAuthorize("hasRole('USER')")` guards. | Track 2 (Security) | **Teammate 2**: Enable `@EnableMethodSecurity` and add `@PreAuthorize("hasRole('ADMIN')")` to catalog mutations and admin order lookup; add `@PreAuthorize("hasRole('USER')")` to cart and checkout routes. |
| **TD-T1-03** | Controller Tests (`ProductControllerTest`, `CartControllerTest`, `OrderControllerTest`, `CheckoutControllerTest`) | **Bypassed Security Filters**: Tests use `@AutoConfigureMockMvc(addFilters = false)` to test HTTP route logic without requiring mock JWT decoders. | Track 2 (Security) & Phase 4 | **Teammate 2**: Implement dedicated security filter integration tests using `@WithMockJwt` verifying JWT claims, signature checks, audience `shopping-cart-api`, and role mapping. |
| **TD-T1-04** | `backend/src/test/resources/application.yml` & `application-test.yml` | **Non-Standard Test Port**: `spring.mongodb.uri` targets port `27018` because host standalone MongoDB occupied port `27017`. | Track 3 (Infra) & Phase 4 | **Teammate 3**: Once `infra/docker-compose.yml` runs single-node replica set `rs0` on standard port `27017`, realign test YAML default URI to `${MONGODB_URI:mongodb://localhost:27017/shopping_cart?replicaSet=rs0&directConnection=true}`. |
| **TD-T1-05** | `CheckoutController.java:31` | **Server-Side Idempotency-Key Fallback**: Generates `UUID.randomUUID().toString()` when client omits the `Idempotency-Key` HTTP header. | Track 3 (Frontend) | **Teammate 3**: In Vue 3 Pinia store / checkout action, generate an action-scoped UUID `Idempotency-Key` header on every user button click so network retries safely replay without duplication. |
| **TD-T1-06** | Product Catalog Storage (`ProductRepository`) | **No Default Catalog Database Seeder**: Catalog products are created exclusively inside test fixtures; no default seed bean exists for local development. | Track 3 (Frontend) & Phase 4 | **Teammate 3**: Add a `ProductDataSeeder` (`CommandLineRunner`) active under `dev` / `docker` profiles to automatically populate default products (Keyboard, Mouse, Monitor) on application startup. |
| **TD-T1-07** | Monolith Root / Web MVC Filter Chain | **CORS Configuration Not Yet Declared**: No `CorsConfigurationSource` bean is configured yet; browser requests from Vite (`http://localhost:5173`) would be blocked by CORS SOP. | Track 2 (Security) | **Teammate 2**: In `SecurityConfig`, register a `CorsConfigurationSource` allowing `http://localhost:5173` and `http://localhost:80` with headers `Authorization`, `Idempotency-Key`, `Content-Type`. |
| **TD-T1-08** | Monolith Root / Logging & Observability | **No MDC Correlation ID Filter**: HTTP requests do not yet extract `X-Request-Id` into SLF4J MDC or echo it into error responses. | Track 2 (Platform API) | **Teammate 2**: Implement `CorrelationIdFilter` adding `X-Request-Id` to MDC and include it in RFC 7807/9457 `ProblemDetail` responses. |

---

## Track 2: Security & Platform API (Teammate 2)
**Branch:** `feature/track2-security-platform`  
**Reference Document:** [architecture-monolith-grp2-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md)

### 1. Changes Done in Track 2
*(Populated as Track 2 tasks are committed)*
- **Files Created/Modified:**
  - `infra/keycloak/realm-export.json`
  - `backend/src/main/java/com/example/shoppingcart/security/**`
  - `backend/src/main/java/com/example/shoppingcart/shared/filter/CorrelationIdFilter.java`
  - `backend/src/main/java/com/example/shoppingcart/shared/error/GlobalExceptionHandler.java`
- **Architectural Integrations:**
  - Single Spring Security 6 Resource Server boundary with JWT validation
  - Audience mapper validation (`shopping-cart-api`) & custom `realm_access.roles` converter
  - Server-authoritative identity extraction from `Jwt.sub` (404 on foreign order access)
  - RFC 7807/9457 `ProblemDetail` responses and SLF4J MDC `X-Request-Id` correlation

### 2. Test Suite Statistics (Track 2)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| JWT Validation Tests (Signature, Exp, Aud) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Route RBAC Tests (Admin vs User) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| IDOR & Ownership Protection Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Bug Fix & Failed Test Resolution Log (Track 2)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| *None logged yet* | - | - | - | - |

### 4. Technical Debt & Mocked Elements Ledger (Track 2)

| Debt ID | Component | Mock / Stub / Debt Description | Target Elimination Phase | Status |
|---|---|---|---|---|
| TD-T2-01 | Security | Mock JWKS decoder used for isolated offline tests | Joint Phase 4 | Active |

---

## Track 3: Infrastructure, Vue 3 SPA & Automated E2E Suite (Teammate 3)
**Branch:** `feature/track3-infra-frontend-tests`  
**Reference Document:** [architecture-monolith-grp3-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp3-ready.md)

### 1. Changes Done in Track 3
*(Populated as Track 3 tasks are committed)*
- **Files Created/Modified:**
  - `infra/docker-compose.yml`
  - `frontend/**`
  - `e2e/**`
  - `backend/src/test/java/com/example/shoppingcart/architecture/**`
  - `backend/src/test/java/com/example/shoppingcart/concurrency/**`
- **Architectural Integrations:**
  - Single-node MongoDB 7 replica set (`rs0`) with automated initiation
  - Vue 3 SPA with Pinia in-memory token store (zero `localStorage` usage)
  - ArchUnit tests guarding package encapsulation and forbidden repository imports
  - Concurrency harness testing cart version clashes and idempotency races
  - Playwright golden path E2E test

### 2. Test Suite Statistics (Track 3)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| ArchUnit Boundary Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Concurrency & Race Condition Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Playwright E2E Golden Path | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Bug Fix & Failed Test Resolution Log (Track 3)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| *None logged yet* | - | - | - | - |

### 4. Technical Debt & Mocked Elements Ledger (Track 3)

| Debt ID | Component | Mock / Stub / Debt Description | Target Elimination Phase | Status |
|---|---|---|---|---|
| TD-T3-01 | Frontend | Mock login toggle switch during UI template prototyping | Joint Phase 4 | Active |

---

## Joint Phase 4: Integration, Hardening & Final Pass (All Teammates)
**Branch:** `main` (Merge of Tracks 1, 2, and 3)

### 1. Changes Done in Joint Phase 4
*(To be detailed upon merging all tracks)*

### 2. Consolidated Test Suite Statistics

| Master Suite | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| Unit & Persistence (Track 1) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Security Integration (Track 2) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Architecture & Concurrency (Track 3) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Playwright Golden Path E2E | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **GRAND TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Master Technical Debt & Mock Elimination Sign-off

| Debt ID | Originating Track | Original Debt Description | Verification of Complete Removal | Sign-off Date |
|---|---|---|---|---|
| **TD-T1-01** | Track 1 | Hardcoded `"user1"` fallback in `SecurityContextPrincipalResolver` | Verified 401 Unauthorized returned on missing/invalid JWT; claims extracted strictly from `Jwt.sub` | Pending |
| **TD-T1-02** | Track 1 | Missing method security annotations (`@PreAuthorize`) on catalog and order endpoints | Verified 403 Forbidden returned when user lacks `ROLE_ADMIN` on admin routes | Pending |
| **TD-T1-03** | Track 1 | Bypassed security filters (`addFilters = false`) in controller tests | Verified Security filter integration tests passing with `@WithMockJwt` | Pending |
| **TD-T1-04** | Track 1 | Test MongoDB replica set port 27018 override | Verified test and application configs standardizing on port 27017 replica set | Pending |
| **TD-T1-05** | Track 1 | Server-side auto-generated UUID fallback for `Idempotency-Key` header | Verified Vue 3 frontend explicitly generates and sends `Idempotency-Key` per checkout click | Pending |
| **TD-T1-06** | Track 1 | Absence of default catalog database seed bean | Verified `ProductDataSeeder` seeds catalog on initial application startup | Pending |
| **TD-T1-07** | Track 1 | Undeclared CORS filter for Vite development server | Verified `CorsConfigurationSource` allows frontend SPA origins | Pending |
| **TD-T1-08** | Track 1 | Absence of MDC logging and correlation ID filter | Verified `CorrelationIdFilter` adds `X-Request-Id` to SLF4J MDC and ProblemDetail | Pending |
| **TD-T2-01** | Track 2 | Mock JWKS decoder used for isolated offline tests | Verified live Keycloak JWKS endpoint validation | Pending |
| **TD-T3-01** | Track 3 | Mock login toggle switch during UI template prototyping | Verified real OIDC Authorization Code + PKCE flow | Pending |
