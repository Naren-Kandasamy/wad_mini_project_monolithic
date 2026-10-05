# Master Implementation Plan: 3 Parallel Development Tracks & Testing Suite

## Standardized Technology Stack & Target Baseline
- **Backend Framework:** Spring Boot 4.1.1
- **Language / Runtime:** Java 21 (LTS)
- **Frontend:** Vue.js 3 SPA (Vite, Pinia, Vue Router)
- **Database:** MongoDB 7.x (Single-node replica set `rs0` for multi-document transaction support)
- **Identity Provider:** Keycloak 24+ (OIDC, Authorization Code with PKCE, realm: `shopping-cart`, audience: `shopping-cart-api`)
- **Testing Tools:** JUnit 5, Mockito, MockMvc, ArchUnit, Playwright E2E

---

## Documents Referred & Contextual Blueprint
Each teammate has a dedicated architectural specification in `Docs/` that serves as their primary reference:

| Document | File Path | Scope & Primary Owner |
|---|---|---|
| **Master Architecture** | [shopping-cart-monolith-master-architecture.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/shopping-cart-monolith-master-architecture.md) | Single consolidated source of truth: 2-hour scope constraints (§65–§66), golden path flow (§70), global architectural decisions table (§76). |
| **Group 1: Core Modular Monolith** | [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md) | **Primary Owner: Teammate 1 (Track 1)**. Module boundaries, typed in-process Java interfaces, MongoDB multi-document transaction demarcated by Checkout, Cart `version` optimistic concurrency, and unique database indexes. |
| **Group 2: Security Architecture** | [architecture-monolith-grp2-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md) | **Primary Owner: Teammate 2 (Track 2)**. Keycloak realm setup (`shopping-cart`), OIDC Authorization Code + PKCE, public Vue client, in-memory token storage (no `localStorage`), single Spring Security resource server validation boundary, JWT audience validation (`shopping-cart-api`), custom converter for `realm_access.roles` (`USER`, `ADMIN`, `DEVELOPER`), server-side identity derivation from `Jwt.sub`, and `/me` ownership model. |
| **Group 3: Testing & Production Readiness** | [architecture-monolith-grp3-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp3-ready.md) | **Primary Owner: Teammate 3 (Track 3)**. 5-layer testing strategy (Unit, Module, Persistence, Security, Concurrency, ArchUnit, Playwright E2E), single-node MongoDB replica set requirement (`--replSet rs0` + `rs.initiate()`), request correlation filter (`X-Request-Id`), structured logging (safe data only), RFC 7807/9457 `ProblemDetail` errors, and `/actuator/health` liveness/readiness probes. |

---

## Parallel Development Model: 1 Track & 1 Git Branch Per Teammate
To ensure zero merge conflicts and unblocked parallel execution, each teammate works exclusively within their isolated track and git branch:

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                               3 PARALLEL WORKSTREAMS (BRANCH-PER-TRACK)                         │
├────────────────────────────┬───────────────────────────────┬────────────────────────────────────┤
│     TRACK 1 / TEAMMATE 1   │      TRACK 2 / TEAMMATE 2     │        TRACK 3 / TEAMMATE 3        │
│   Branch: `feature/track1` │    Branch: `feature/track2`   │      Branch: `feature/track3`      │
│     Ref: Group 1 Doc       │        Ref: Group 2 Doc       │          Ref: Group 3 Doc          │
├────────────────────────────┼───────────────────────────────┼────────────────────────────────────┤
│ **Core Domain & Tx Engine**│ **Security & Platform API**   │ **Infra, Vue 3 SPA & E2E Suite**   │
│ Scope:                     │ Scope:                        │ Scope:                             │
│ • Product, Cart, Order,    │ • Keycloak Realm & Docker     │ • Docker Compose (Mongo RS `rs0`)  │
│   Checkout modules         │ • Spring Security Config      │ • Vue 3 SPA + Vite + Tailwind      │
│ • MongoDB Models & Repos   │ • JWT Claims Converter        │ • Pinia In-Memory Auth Store       │
│ • `@Transactional` Flow    │ • Route RBAC Rules            │ • Catalog, Cart, Checkout Views    │
│ • Cart Optimistic Version  │ • Server-Side Ownership Check │ • ArchUnit Boundary Tests          │
│ • Idempotency DB Index     │ • RFC 7807 ProblemDetails     │ • Concurrency Test Harness         │
│ • Domain Unit Tests        │ • Correlation Filter (MDC)    │ • Playwright Golden Path E2E       │
│ • Mongo Persistence Tests  │ • Security MockMvc Tests      │ • Actuator Health/Readiness Probes │
└────────────────────────────┴───────────────────────────────┴────────────────────────────────────┘
                                              │
                                              ▼
                          ┌───────────────────────────────────────┐
                          │     JOINT MERGE & VERIFICATION PASS   │
                          │   Merge Tracks to `main` → Run E2E    │
                          └───────────────────────────────────────┘
```

---

## Track 1: Core Domain & Transaction Engine
**Owner:** Teammate 1  
**Git Branch:** `feature/track1-core-domain`  
**Reference Document:** [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md)  
**File Scope:** `backend/src/main/java/com/example/shoppingcart/{product, cart, order, checkout}/**`, `backend/src/test/java/com/example/shoppingcart/{product, cart, order, checkout}/**`

### Phase 1.1: Domain Models & Public API Contracts
- [ ] Initialize domain entities:
  - `ProductDocument` (`id`, `name`, `description`, `price`, `sku`, `active`)
  - `CartDocument` (`id`, `userId`, `version`, `items[]`, `updatedAt`)
  - `OrderDocument` (`id`, `userId`, `idempotencyKey`, `items[]`, `totalAmount`, `status`, `createdAt`, `updatedAt`)
- [ ] Define Java 21 public record snapshots:
  - `ProductSnapshot(String id, String name, BigDecimal price, boolean active)`
  - `CartItemSnapshot(String productId, String productName, BigDecimal unitPrice, int quantity)`
  - `CheckoutCart(String userId, long version, List<CartItemSnapshot> items, BigDecimal subtotal)`
  - `OrderSnapshot(String id, String userId, String idempotencyKey, List<CartItemSnapshot> items, BigDecimal totalAmount, String status, Instant createdAt)`
- [ ] Freeze in-process typed module API interfaces:
  - `ProductApi.java` -> `Optional<ProductSnapshot> getAvailableProduct(String productId)`
  - `CartApi.java` -> `CheckoutCart loadForCheckout(String userId)`, `void finalizeCheckout(String userId, long expectedVersion)`
  - `OrderApi.java` -> `OrderSnapshot createOrder(...)`, `Optional<OrderSnapshot> findByUserIdAndIdempotencyKey(...)`, `Optional<OrderSnapshot> findByIdAndUserId(...)`, `Optional<OrderSnapshot> findByIdForAdmin(...)`

### Phase 1.2: Product & Cart Modules Implementation
- [ ] Implement `ProductService` & `ProductController`:
  - `GET /api/products` (list active products)
  - `GET /api/products/{id}` (single product details)
  - `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}` (admin catalog mutations)
  - Validation: Positive price, mandatory name & SKU
- [ ] Implement `CartService` & `CartController`:
  - `GET /api/carts/me`, `POST /api/carts/me/items`, `PUT /api/carts/me/items/{productId}`, `DELETE /api/carts/me/items/{productId}`, `DELETE /api/carts/me`
  - Item addition calls `ProductApi.getAvailableProduct()` to validate existence and store server-authoritative snapshot price
  - Maintain optimistic `version` (incremented on every write)
  - Ensure unique index: `unique(userId)` on `carts` collection

### Phase 1.3: Order Module & Transactional Checkout Workflow
- [ ] Implement `OrderService` & `OrderController`:
  - `GET /api/orders` (list caller's orders)
  - `GET /api/orders/{id}` (fetch single order with ownership validation)
  - `GET /api/admin/orders/{orderId}` (administrative order lookup)
  - Ensure indexes: `unique(userId, idempotencyKey)` and `index(userId)` on `orders` collection
- [ ] Implement `CheckoutService`:
  - `POST /api/orders/checkout` with `Idempotency-Key` header
  - Demarcate `@Transactional` (Spring MongoDB multi-document transaction)
  - Workflow execution:
    1. Authenticate caller identity
    2. Check if order with `(userId, idempotencyKey)` already exists; if found, replay committed order immediately
    3. Load cart via `CartApi.loadForCheckout(userId)`
    4. Reject empty/nonexistent cart with `CartEmptyException` (409 Conflict)
    5. Calculate order total strictly from server-owned cart snapshots
    6. Persist immutable `OrderDocument` with status `CONFIRMED`
    7. Atomically finalize cart via `CartApi.finalizeCheckout(userId, expectedVersion)`. If version changed during checkout, throw `CartVersionConflictException` (aborts transaction -> 409 Conflict)
    8. Commit transaction and return confirmed order DTO
    9. Catch MongoDB duplicate key race: re-fetch committed order and return it (never return generic 500)

### Phase 1.4: Unit Tests & Persistence Verification
- [ ] Unit tests: Price calculations, subtotal arithmetic, quantity boundaries, immutable copying
- [ ] Persistence integration tests:
  - Successful checkout transaction commits both Order creation and Cart clearing
  - Transaction rollback test: Injected failure rolls back both Order and Cart writes
  - Idempotency uniqueness test: Database constraint rejects duplicate `(userId, idempotencyKey)`

---

## Track 2: Security & Platform API
**Owner:** Teammate 2  
**Git Branch:** `feature/track2-security-platform`  
**Reference Document:** [architecture-monolith-grp2-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md)  
**File Scope:** `infra/keycloak/**`, `backend/src/main/java/com/example/shoppingcart/security/**`, `backend/src/main/java/com/example/shoppingcart/shared/**`, `backend/src/test/java/com/example/shoppingcart/security/**`

### Phase 2.1: Keycloak Docker Configuration & Realm Setup
- [ ] Define Keycloak 24+ container configuration:
  - Port `8180`, realm: `shopping-cart`
  - Client: `shopping-cart-spa` (public client, PKCE enabled, redirect URIs `http://localhost:5173/*`, web origin `http://localhost:5173`)
  - Roles: `USER`, `ADMIN`, `DEVELOPER`
  - Audience Mapper: Emit `shopping-cart-api` claim in access token `aud`
  - Seed test users: `user1` (role `USER`), `admin1` (role `ADMIN`), `dev1` (role `DEVELOPER`)
- [ ] Create automated realm import export file (`infra/keycloak/realm-export.json`)

### Phase 2.2: Spring Security Resource Server Configuration
- [ ] Configure `SecurityFilterChain`:
  - OAuth2 Resource Server with JWT validation
  - Validate signature against Keycloak JWKS endpoint
  - Validate issuer URL (`KEYCLOAK_ISSUER_URI`)
  - Validate expiration timestamps
  - Custom audience validator enforcing `shopping-cart-api`
- [ ] Implement `KeycloakJwtAuthenticationConverter`:
  - Extract nested `realm_access.roles` array
  - Map each role to `ROLE_USER`, `ROLE_ADMIN`, `ROLE_DEVELOPER` authorities

### Phase 2.3: Route RBAC, Ownership Enforcement & CORS
- [ ] Configure route authorization rules:
  - `POST`, `PUT`, `DELETE /api/products/**` -> `hasRole('ADMIN')`
  - `GET /api/admin/**` -> `hasRole('ADMIN')`
  - `GET /api/products/**` -> `permitAll()`
  - `/api/carts/me/**` -> `hasRole('USER')` / `authenticated()`
  - `/api/orders/**` -> `authenticated()`
  - `/actuator/health/**` -> `permitAll()`
- [ ] Implement server-side identity & IDOR protection:
  - Canonical user ID derived strictly from `Authentication.getName()` / `Jwt.getSubject()`
  - Reject/ignore any client-provided `userId`, `X-User-Id`, or `X-User-Role` headers
  - In `GET /api/orders/{id}`: Return `404 Not Found` if requested order does not belong to caller and caller is not ADMIN
- [ ] Configure application CORS:
  - Allowed origin: `http://localhost:5173`
  - Allowed methods: `GET, POST, PUT, DELETE, OPTIONS`
  - Allowed headers: `Authorization, Content-Type, Idempotency-Key, X-Request-Id`

### Phase 2.4: Cross-Cutting Operations & Security Tests
- [ ] Implement `CorrelationIdFilter`: Generate or propagate `X-Request-Id` to SLF4J MDC
- [ ] Implement RFC 7807/9457 `ProblemDetail` Global Exception Handler (mapping 400, 401, 403, 404, 409, 500 without leaking stack traces or secrets)
- [ ] Spring Security `MockMvc` test suite:
  - Missing JWT -> `401 Unauthorized`
  - Expired / tampered JWT -> `401 Unauthorized`
  - Wrong audience -> `401 Unauthorized`
  - Non-admin calling `POST /api/products` -> `403 Forbidden`
  - User A fetching User B's order -> `404 Not Found`
  - Forged identity headers ignored

---

## Track 3: Infrastructure, Vue 3 SPA & Automated E2E Suite
**Owner:** Teammate 3  
**Git Branch:** `feature/track3-infra-frontend-tests`  
**Reference Document:** [architecture-monolith-grp3-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp3-ready.md)  
**File Scope:** `infra/docker-compose.yml`, `frontend/**`, `e2e/**`, `backend/src/test/java/com/example/shoppingcart/architecture/**`, `backend/src/test/java/com/example/shoppingcart/concurrency/**`

### Phase 3.1: Docker Compose Environment
- [ ] Configure `infra/docker-compose.yml`:
  - MongoDB 7.x service configured with `--replSet rs0`
  - Initialization container/script executing `rs.initiate()`
  - Keycloak 24+ service importing `shopping-cart` realm
  - Healthcheck probes for Mongo replica set readiness

### Phase 3.2: Vue 3 SPA Scaffolding & In-Memory Auth Store
- [ ] Initialize Vue 3 project with Vite, Pinia, Vue Router, Tailwind CSS
- [ ] Implement OIDC PKCE authentication service (`oidc-client-ts` / Keycloak JS):
  - Authorization Code flow with PKCE
  - Pinia `useAuthStore`: Access token stored **strictly in-memory** (never `localStorage`)
- [ ] Configure Axios HTTP client:
  - Request interceptor attaching `Authorization: Bearer <token>` and `X-Request-Id`
  - Response interceptor handling 401 and 409 ProblemDetails

### Phase 3.3: Frontend UI Views & Navigation
- [ ] Build UI Views:
  - **Navbar / Auth Bar:** Shows login button, logged-in username, user roles, logout button
  - **Catalog View:** Grid of available products fetched from `/api/products`; "Add to Cart" action
  - **Cart View (`/cart`):** Fetches `/api/carts/me`, updates item quantities, displays subtotal, remove buttons
  - **Checkout Action:** Generates UUID `Idempotency-Key` header, submits checkout request, handles 409 version conflict prompt
  - **Order Confirmation & History (`/orders`):** Order summary with `CONFIRMED` status and past purchase history
  - **Admin Products View (`/admin/products`):** Create/edit product catalog (rendered only for `ROLE_ADMIN`)

### Phase 3.4: Automated Test Harness & Probes
- [ ] Implement ArchUnit tests (`ArchUnitTest.java`):
  - Verify `cart`, `order`, `product` internal packages are never imported cross-module
  - Verify Controllers never import Repositories directly
  - Verify no cyclic package dependencies
- [ ] Implement Concurrency Integration Tests:
  - Cart version conflict test: Thread A loads v1, Thread B adds item (v2), Thread A commits v1 -> receives `409 Conflict`
  - Idempotency race test: 2 concurrent threads post same key -> 1 order persisted, both receive same confirmed order ID
- [ ] Implement Playwright E2E Golden Path (`e2e/golden-path.spec.ts`):
  - Login as `user1` -> Browse catalog -> Add item -> View cart -> Checkout -> Verify `CONFIRMED` order -> Verify cart empty
- [ ] Configure Actuator `/actuator/health` liveness & readiness probes

---

## Joint Phase 4: Integration, Consolidation & Final Pass
**Owners:** All 3 Teammates  
**Target Branch:** `main` (Merge `feature/track1-core-domain`, `feature/track2-security-platform`, `feature/track3-infra-frontend-tests`)

- [ ] Merge all 3 feature branches into `main`
- [ ] Execute full automated test suite:
  ```bash
  mvn test
  cd frontend && npx playwright test
  ```
- [ ] Perform live smoke verification:
  - Verify `/actuator/health` returns `UP`
  - Verify Keycloak realm configuration
  - Verify complete browser flow on `http://localhost:5173`
- [ ] Review and clear all items in the Technical Debt & Mocked Elements Ledger in [walkthrough.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/walkthrough.md)
