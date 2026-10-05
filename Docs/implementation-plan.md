# Master Implementation Plan Template & Architecture Reference

## Standardized Technology Stack & Target Baseline
- **Backend Framework:** Spring Boot 4.1.1
- **Language / Runtime:** Java 21 (LTS)
- **Frontend:** Vue.js 3 SPA (Vite, Pinia, Vue Router)
- **Database:** MongoDB 7.x (Single-node replica set `rs0` for multi-document transaction support)
- **Identity Provider:** Keycloak 24+ (OIDC, Authorization Code with PKCE, realm: `shopping-cart`, audience: `shopping-cart-api`)
- **Testing Tools:** JUnit 5, Mockito, MockMvc, ArchUnit, Playwright E2E

## Documents Referred & Contextual Blueprint
Before executing any implementation phase, developers must refer to the following authoritative documents located in `Docs/`:

| Document | File Path | Scope & Context When Building |
|---|---|---|
| **Master Architecture** | [shopping-cart-monolith-master-architecture.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/shopping-cart-monolith-master-architecture.md) | Single consolidated source of truth for the entire system: 2-hour scope constraints (§65–§66), golden path flow (§70), global architectural decisions table (§76), and operational non-goals (§68). |
| **Group 1: Core Modular Monolith** | [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md) | Package structure rules (`api` vs `internal`), module dependencies (Product, Cart, Order, Checkout), in-process typed Java interfaces, MongoDB multi-document transaction demarcated by Checkout, Cart `version` optimistic concurrency, and unique database indexes. |
| **Group 2: Security Architecture** | [architecture-monolith-grp2-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md) | Keycloak realm setup (`shopping-cart`), OIDC Authorization Code + PKCE, public Vue client, in-memory token storage (no `localStorage`), single Spring Security resource server validation boundary, JWT audience validation (`shopping-cart-api`), custom converter for `realm_access.roles` (`USER`, `ADMIN`, `DEVELOPER`), server-side identity derivation from `Jwt.sub`, and `/me` ownership model. |
| **Group 3: Testing & Production Readiness** | [architecture-monolith-grp3-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp3-ready.md) | 5-layer testing strategy (Unit, Module, Persistence, Security, Concurrency, ArchUnit, Playwright E2E), single-node MongoDB replica set requirement (`--replSet rs0` + `rs.initiate()`), request correlation filter (`X-Request-Id`), structured logging (safe data only), RFC 7807/9457 `ProblemDetail` errors, and `/actuator/health` liveness/readiness probes. |

---

## Plan Structure Notice: Master Template vs. Phase-Specific Plans
> [!NOTE]
> This document acts as the **Master Implementation Plan & Template**.  
> For actual implementation execution, a dedicated, granular implementation plan will be spawned for **each individual phase** (e.g., `Docs/phase-1-implementation-plan.md`, `Docs/phase-2-implementation-plan.md`, etc.). Each phase-specific plan will adopt this template, define concrete tasks per teammate, specify exact file diffs, and feed directly into the cumulative [walkthrough.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/walkthrough.md).

---

## 1. Architectural Alignment Analysis
A comparative audit of `Docs/shopping-cart-monolith-master-architecture.md` against individual group architecture documents confirms complete alignment:

| Dimension | Group 1 (Core) | Group 2 (Security) | Group 3 (Ops & Test) | Master Architecture | Alignment Status |
|---|---|---|---|---|---|
| **Runtime Topology** | Single Spring Boot monolith; no API Gateway | Single application security boundary; no inter-service REST | Single deployable artifact; no service discovery | Single Spring Boot monolith runtime | **Fully Aligned** |
| **Modules** | Product, Cart, Order, Checkout | Secures all 4 modules uniformly | Verifies boundaries of all 4 modules | Product, Cart, Order, Checkout, Security | **Fully Aligned** |
| **Inter-module Calls** | In-process typed Java APIs (`ProductSnapshot`, `CheckoutCart`) | Uses in-process ambient security context (no JWT forwarding) | Tests verify in-process contracts; forbidden repo imports | In-process typed module APIs | **Fully Aligned** |
| **Checkout Consistency** | Single MongoDB multi-document transaction | Enforces server-authoritative pricing | Real replica-set transaction tests; rollback on failure | Single MongoDB transaction owned by Checkout | **Fully Aligned** |
| **Concurrency Control** | Cart `version` optimistic locking (409 on mismatch) | Cart version prevents race on checkout | Concurrency test suite tests version clash | Cart `version` -> 409 Conflict, no silent retry | **Fully Aligned** |
| **Idempotency** | Unique `(userId, idempotencyKey)` on orders | Server validates idempotency race handling | Tests concurrent same-key races; re-reads committed order | Database unique constraint; duplicate race re-reads order | **Fully Aligned** |
| **Order Lifecycle** | Synchronous baseline: `CONFIRMED` only | Terminal state upon commit | Rollback leaves no partial/`FAILED` order | Terminal state `CONFIRMED`; no durable `FAILED` order | **Fully Aligned** |
| **Identity & Auth** | JWT `sub` as canonical identity | Keycloak OIDC Auth Code + PKCE, in-memory tokens, `shopping-cart-api` aud | Verifies 401, 403, 404, IDOR, forged headers | Keycloak OIDC, in-memory tokens, JWT `sub`, RBAC | **Fully Aligned** |
| **Roles & RBAC** | Admin product mutation | `USER`, `ADMIN`, `DEVELOPER`; custom converter for `realm_access.roles` | Security tests assert role access | `USER`, `ADMIN`, `DEVELOPER`; custom converter | **Fully Aligned** |
| **Persistence** | 1 MongoDB; owned collections: `products`, `carts`, `orders` | Same database, logical ownership | Single-node replica set required for transactions | 1 MongoDB, strict logical collection ownership | **Fully Aligned** |
| **Observability** | Request ID, structured logs, Actuator | No token/secret logging | Request ID filter, MDC, Actuator health/readiness, Micrometer | Application-level logging, correlation ID, metrics | **Fully Aligned** |

---

## 2. Parallel Workstream Division (3 Teammates)

```text
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           TEAMMATE WORK ALLOCATION                              │
├────────────────────────┬───────────────────────────────┬────────────────────────┤
│      TEAMMATE 1        │          TEAMMATE 2           │       TEAMMATE 3       │
│  Domain & Core Modules │  Security & Cross-Cutting Ops │ Infra, Frontend & Test │
├────────────────────────┼───────────────────────────────┼────────────────────────┤
│ • Product Module       │ • Keycloak Realm & Docker     │ • Docker Compose (RS)  │
│ • Cart Module          │ • Spring Security Config      │ • Vue.js 3 SPA App     │
│ • Order Module         │ • JWT Validator & Roles       │ • Pinia Auth & Router  │
│ • Checkout Service     │ • Problem Details (Errors)    │ • ArchUnit Tests       │
│ • Mongo Repos & Models │ • Correlation Filter & Logs   │ • Concurrency Tests    │
│ • Module Domain Tests  │ • Security Test Suite         │ • Playwright E2E Tests │
└────────────────────────┴───────────────────────────────┴────────────────────────┘
```

---

## 3. Master Phase Breakdown & Templates

### Phase 1: Foundation, Infrastructure & Contract Freezing (Time: 0.0h – 0.5h)
- **Teammate 1 (Domain Core):**
  - Initialize Spring Boot project layout (`com.example.shoppingcart.{product, cart, order, checkout}`).
  - Lock typed module public Java interfaces (`ProductApi`, `CartApi`, `OrderApi`).
  - Define document entities (`ProductDocument`, `CartDocument`, `OrderDocument`) and baseline DTOs.
- **Teammate 2 (Security & Ops):**
  - Keycloak 24+ Docker Compose definition with `shopping-cart` realm, `shopping-cart-spa` client (PKCE, public client), `USER`/`ADMIN`/`DEVELOPER` roles, and `shopping-cart-api` audience mapper.
  - Spring Security Resource Server configuration skeleton, JWT converter, Correlation ID filter (`X-Request-Id`), and `ProblemDetail` error handler.
- **Teammate 3 (Infra & Frontend):**
  - Docker Compose environment with single-node MongoDB 7.x replica set (`--replSet rs0` + automated `rs.initiate()` healthcheck).
  - Vue 3 Vite application with Pinia in-memory token store, OIDC PKCE client, and Axios instance configured with `Authorization` and `X-Request-Id` interceptors.
  - Test framework initialization (JUnit 5, Mockito, AssertJ, Playwright).

### Phase 2: Core Implementation & Integration (Time: 0.5h – 1.25h)
- **Teammate 1 (Domain Core):**
  - Implement Product catalog endpoints (`GET /api/products`, `POST /api/products`).
  - Implement Cart endpoints (`/api/carts/me/**`), subtotal calculations, `version` increment logic, and `unique(userId)` index.
  - Implement Order persistence with `unique(userId, idempotencyKey)` and `index(userId)`.
  - Implement transactional Checkout workflow:
    1. Demarcates `@Transactional` on CheckoutService.
    2. Validates idempotency key (replays committed order if present).
    3. Loads cart snapshot and validates cart is not empty.
    4. Creates immutable `OrderDocument` with status `CONFIRMED`.
    5. Finalizes cart with optimistic version check (aborts with 409 Conflict if version mismatch).
    6. Commits transaction and handles concurrent duplicate key races cleanly.
- **Teammate 2 (Security & Ops):**
  - Wire Spring Security route rules (`POST /api/products/**` -> `hasRole('ADMIN')`, `/api/carts/me/**` -> `authenticated()`, etc.).
  - Enforce server-side identity derivation strictly from `Jwt.sub` (preventing IDOR; return 404 on foreign order access).
  - Configure strict CORS for `http://localhost:5173`.
  - Finalize RFC 7807/9457 `ProblemDetail` handlers (400, 401, 403, 404, 409, 500).
- **Teammate 3 (Frontend Views):**
  - Build Vue UI views: Login/User Bar, Catalog View, Cart View with quantity controls, Checkout button (generating UUID `Idempotency-Key`), Order Confirmation view, and Order History.

### Phase 3: Comprehensive Testing Suite (Time: 1.25h – 1.75h)
- **Teammate 1 (Domain & Persistence Tests):**
  - Unit tests for calculations, quantity boundaries, and immutable snapshots.
  - MongoDB replica set integration tests: successful commit and rollback verification on injected failure.
  - Database unique index tests on `(userId, idempotencyKey)`.
- **Teammate 2 (Security Integration Tests):**
  - `MockMvc` tests verifying 401 Unauthorized (missing, expired, wrong issuer, wrong audience).
  - RBAC tests: regular `USER` calling `POST /api/products` -> 403 Forbidden; `ADMIN` -> 201 Created.
  - IDOR tests: User A requesting User B's order -> 404 Not Found.
  - Forged headers test: `X-User-Id` and `X-User-Role` headers ignored.
- **Teammate 3 (ArchUnit, Concurrency & E2E):**
  - ArchUnit tests: enforce module package encapsulation; forbid controllers importing repositories.
  - Concurrency tests:
    - Cart version conflict test: Thread A loads v1, Thread B updates cart to v2, Thread A checkout aborts with 409 Conflict.
    - Idempotency race test: 2 concurrent threads submit identical key; one wins, loser re-reads committed order; exactly 1 order persisted.
  - Playwright E2E golden-path test: Browser login -> add item -> update cart -> checkout -> order confirmed -> cart cleared.

### Phase 4: Observability, Smoke Verification & Hardening (Time: 1.75h – 2.0h)
- **All Teammates:**
  - Verify Actuator `/actuator/health` liveness & readiness probes.
  - Validate MDC structured logging with correlation IDs (ensure no tokens/secrets logged).
  - Run full test suite (`mvn test`) and Playwright E2E.
  - Review and eliminate technical debt and temporary stubs.

---

## 4. Verification Plan

### Automated Execution
```bash
# Unit & Domain Tests
mvn test -Dtest=*UnitTest

# Security Tests
mvn test -Dtest=*SecurityTest

# Architecture Tests
mvn test -Dtest=*ArchitectureTest

# Replica Set Transaction & Concurrency Tests
mvn test -Dtest=*TransactionIntegrationTest,*ConcurrencyTest

# Playwright E2E Golden Path
cd frontend && npx playwright test
```

### Operational Smoke Verification
- Health probe: `curl -s http://localhost:8080/actuator/health`
- Keycloak discovery: `curl -s http://localhost:8180/realms/shopping-cart/.well-known/openid-configuration`
- UI walkthrough: Open `http://localhost:5173` and complete golden checkout path.
