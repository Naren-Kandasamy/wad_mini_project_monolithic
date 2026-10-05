# Implementation Plan: Track 1 — Core Domain & Transaction Engine

**Owner:** Teammate 1  
**Target Git Branch:** `feature/track1-core-domain`  
**Technology Baseline:** Spring Boot 4.1.1 + Java 21 (LTS) + MongoDB 7.x (Replica Set `rs0`)  
**Scope:** Core domain modules (`product`, `cart`, `order`, `checkout`), MongoDB models & repositories, `@Transactional` checkout workflow, cart versioning, idempotency database index, and persistence integration tests.

---

## Documents Referred & Contextual Blueprint
Before implementing Track 1, developers must refer to the following authoritative documents in `Docs/`:

| Document | File Path | Scope & Context When Building Track 1 |
|---|---|---|
| **Master Architecture** | [shopping-cart-monolith-master-architecture.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/shopping-cart-monolith-master-architecture.md) | Authoritative reference for module boundaries (§6), typed in-process APIs (§7), checkout consistency & transaction boundary rules (§15–§16), cart optimistic concurrency (§18), idempotency architecture (§19), and order status rules (§20: baseline is strictly `CONFIRMED`, rollback on failure, no durable `FAILED` state). |
| **Group 1: Core Modular Monolith** | [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md) | **Primary Track 1 Blueprint**. Detailed definitions of `ProductSnapshot`, `CheckoutCart`, `ProductApi`, `CartApi`, `OrderApi`, MongoDB collection ownership (`products`, `carts`, `orders`), cart version mismatch abort semantics (409 Conflict, no automatic retry), and idempotency duplicate-key race re-read resolution. |
| **Master Implementation Plan** | [implementation-plan.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/implementation-plan.md) | Master multi-track coordination reference. Sets up branch isolation (`feature/track1-core-domain`) and integration interface contracts so Track 1 runs in parallel without colliding with Track 2 (Security) or Track 3 (Frontend/Infra). |
| **Cumulative Walkthrough** | [walkthrough.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/walkthrough.md) | Project verification and technical debt journal. Track 1 test statistics, bug fixes, and mock tracking ledgers will be logged here upon completion of each phase. |

---

## User Review Required
> [!IMPORTANT]
> - **Branching Strategy:** Track 1 will be developed on the dedicated branch `feature/track1-core-domain` branched off `main`.
> - **Security Decoupling during Track 1 Development:** To allow Teammate 1 to build and test domain logic, cart operations, and checkout transactions completely unblocked before Teammate 2 merges the live Keycloak resource server, Track 1 will resolve the authenticated `userId` through an injectable `SecurityContextPrincipalResolver` interface. In Track 1 unit/persistence tests, this defaults to a mock authenticated subject (e.g., `user1`), which will automatically bind to `Jwt.getSubject()` once Track 2 is merged.
> - **MongoDB Transaction Requirement:** Multi-document transaction tests for Phase 1.4 require a MongoDB replica set (`--replSet rs0`). Tests will utilize a local MongoDB replica set or Spring Boot `@DataMongoTest` / Testcontainers replica set configuration.

---

## Open Questions
- None blocking. Java 21 `record` types will be used for all snapshots and immutable transfer objects (`ProductSnapshot`, `CartItemSnapshot`, `CheckoutCart`, `OrderSnapshot`).

---

## Proposed Changes (Track 1 Phase-by-Phase)

### Phase 1.1: Backend Scaffolding, Models & Public Module API Contracts
Establish the Spring Boot 4.1.1 project layout and freeze public module contracts.

#### [NEW] [pom.xml](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/backend/pom.xml)
- Configure `spring-boot-starter-parent` `4.1.1` and `java.version` `21`.
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-test`.

#### [NEW] [ShoppingApplication.java](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/backend/src/main/java/com/example/shoppingcart/ShoppingApplication.java)
- Main entry point for the monolithic application.

#### [NEW] [application.yml](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/backend/src/main/resources/application.yml)
- Configure MongoDB URI: `mongodb://localhost:27017/shopping_cart?replicaSet=rs0`.
- Enable MongoDB transactions via `MongoTransactionManager` bean.

#### [NEW] Module Public Contracts & Record Snapshots:
- `backend/src/main/java/com/example/shoppingcart/product/api/`:
  - `ProductSnapshot.java`: `public record ProductSnapshot(String id, String name, String description, BigDecimal price, String sku, boolean active)`
  - `ProductApi.java`: `Optional<ProductSnapshot> getAvailableProduct(String productId);`
- `backend/src/main/java/com/example/shoppingcart/cart/api/`:
  - `CartItemSnapshot.java`: `public record CartItemSnapshot(String productId, String productName, BigDecimal unitPrice, int quantity)`
  - `CheckoutCart.java`: `public record CheckoutCart(String userId, long version, List<CartItemSnapshot> items, BigDecimal subtotal)`
  - `CartApi.java`: `CheckoutCart loadForCheckout(String userId);`, `void finalizeCheckout(String userId, long expectedVersion);`
- `backend/src/main/java/com/example/shoppingcart/order/api/`:
  - `OrderSnapshot.java`: `public record OrderSnapshot(String id, String userId, String idempotencyKey, List<CartItemSnapshot> items, BigDecimal totalAmount, String status, Instant createdAt)`
  - `CreateOrderCommand.java`: `public record CreateOrderCommand(String userId, String idempotencyKey, List<CartItemSnapshot> items, BigDecimal totalAmount)`
  - `OrderApi.java`: `OrderSnapshot createOrder(CreateOrderCommand command);`, `Optional<OrderSnapshot> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);`, `Optional<OrderSnapshot> findByIdAndUserId(String orderId, String userId);`, `Optional<OrderSnapshot> findByIdForAdmin(String orderId);`

#### [NEW] Domain Persistence Models:
- `ProductDocument.java`: Annotated with `@Document(collection = "products")`, fields: `id`, `name`, `description`, `price`, `sku`, `active`, `createdAt`, `updatedAt`.
- `CartDocument.java`: Annotated with `@Document(collection = "carts")`, fields: `id`, `@Indexed(unique = true) String userId`, `long version`, `List<CartItem> items`, `Instant updatedAt`.
- `OrderDocument.java`: Annotated with `@Document(collection = "orders")`, fields: `id`, `@Indexed String userId`, `String idempotencyKey`, `List<OrderItem> items`, `BigDecimal totalAmount`, `String status`, `Instant createdAt`, `Instant updatedAt`. Compound index: `@CompoundIndex(name = "user_idempotency_idx", def = "{'userId': 1, 'idempotencyKey': 1}", unique = true)`.

---

### Phase 1.2: Product & Cart Modules Implementation
Implement catalog logic and cart state operations with optimistic versioning.

#### [NEW] Product Module:
- `ProductRepository.java`: Spring Data MongoDB repository for `ProductDocument`.
- `ProductService.java` & `ProductServiceImpl.java`:
  - Implements `ProductApi`.
  - Methods: `listActiveProducts()`, `getProductById(id)`, `createProduct(req)`, `updateProduct(id, req)`, `deleteProduct(id)`.
  - Validates positive price, mandatory SKU & name.
- `ProductController.java`:
  - `GET /api/products`: returns list of active products.
  - `GET /api/products/{id}`: returns product detail.
  - `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}`: catalog management.

#### [NEW] Cart Module:
- `CartRepository.java`: Spring Data MongoDB repository for `CartDocument`.
- `CartService.java` & `CartServiceImpl.java`:
  - Implements `CartApi`.
  - Item addition calls `ProductApi.getAvailableProduct(productId)` to validate existence and store server-authoritative price snapshot.
  - Calculates subtotal: `sum(unitPrice * quantity)`.
  - Increments `version` on every modification.
  - `finalizeCheckout(userId, expectedVersion)`: performs atomic conditional update `{ userId: userId, version: expectedVersion }` clearing items. If modified count == 0, throws `CartVersionConflictException`.
- `CartController.java`:
  - `GET /api/carts/me`: retrieves caller's cart.
  - `POST /api/carts/me/items`: adds item.
  - `PUT /api/carts/me/items/{productId}`: updates item quantity.
  - `DELETE /api/carts/me/items/{productId}`: removes item.
  - `DELETE /api/carts/me`: clears cart.

---

### Phase 1.3: Order Module & Transactional Checkout Workflow
Implement historical order persistence and atomic multi-document checkout coordination.

#### [NEW] Order Module:
- `OrderRepository.java`: Spring Data MongoDB repository for `OrderDocument`.
- `OrderService.java` & `OrderServiceImpl.java`:
  - Implements `OrderApi`.
  - Creates immutable snapshot order with status `CONFIRMED`.
  - Enforces ownership: `findByIdAndUserId(orderId, userId)`.
- `OrderController.java`:
  - `GET /api/orders`: list orders for caller.
  - `GET /api/orders/{id}`: get order by ID (verifies ownership).
  - `GET /api/admin/orders/{orderId}`: admin lookup.

#### [NEW] Checkout Module:
- `CheckoutService.java`:
  - `@Transactional`: Demarcates Spring-managed MongoDB multi-document transaction.
  - Method `processCheckout(String userId, String idempotencyKey)`:
    1. Check if order with `(userId, idempotencyKey)` already exists in `OrderApi`. If exists, replay and return it immediately.
    2. Load cart snapshot via `CartApi.loadForCheckout(userId)`.
    3. If cart is null or items empty, throw `CartEmptyException` (maps to 409 Conflict).
    4. Calculate total amount from server-owned snapshots.
    5. Persist `OrderDocument` with status `CONFIRMED` via `OrderApi.createOrder(...)`.
    6. Finalize cart via `CartApi.finalizeCheckout(userId, cart.version())`. If version mismatch, throws `CartVersionConflictException` (transaction automatically rolls back, returns 409 Conflict).
    7. Commit transaction and return `CheckoutResponse`.
    8. Catch `DuplicateKeyException` on concurrent race: re-fetch committed order and return it cleanly (preventing generic 500 error).
- `CheckoutController.java`:
  - `POST /api/orders/checkout` accepting `Idempotency-Key` header.

---

### Phase 1.4: Unit Tests & Persistence Verification Suite
Implement automated tests verifying domain arithmetic, snapshot immutability, uniqueness constraints, and transaction rollback.

#### [NEW] Unit Tests:
- `CartCalculationUnitTest.java`: validates subtotal calculations, zero/negative quantity rejection, and cart item additions.
- `OrderSnapshotUnitTest.java`: validates that subsequent product price modifications do not alter existing order snapshots.
- `ProductValidationUnitTest.java`: validates price constraints and SKU uniqueness.

#### [NEW] Persistence & Transaction Integration Tests:
- `CheckoutTransactionIntegrationTest.java`:
  - Tests successful checkout: verifies Order document is created and Cart document is cleared in MongoDB.
  - Tests transaction rollback: injects failure before commit; verifies Order is NOT created and Cart items remain intact.
- `CartVersionConflictIntegrationTest.java`:
  - Verifies that checkout with stale cart version aborts with 409 Conflict without clearing newer cart data.
- `IdempotencyRaceIntegrationTest.java`:
  - Verifies unique constraint on `(userId, idempotencyKey)` prevents duplicate orders under duplicate requests.

---

## Verification Plan

### Automated Execution
```bash
# Run domain unit tests
mvn test -Dtest=*UnitTest

# Run persistence and transaction integration tests
mvn test -Dtest=*IntegrationTest
```

### Manual API Smoke Verification
Once the Spring Boot application is started (`mvn spring-boot:run`):
1. **Product Catalog:**
   ```bash
   curl -s http://localhost:8080/api/products
   ```
2. **Cart Management:**
   ```bash
   curl -s -X POST http://localhost:8080/api/carts/me/items \
     -H "Content-Type: application/json" \
     -d '{"productId":"prod-1","quantity":2}'
   ```
3. **Checkout with Idempotency Key:**
   ```bash
   curl -s -X POST http://localhost:8080/api/orders/checkout \
     -H "Idempotency-Key: test-key-001"
   ```
4. **Idempotency Replay Verification:**
   - Execute the exact same checkout command with `test-key-001`. Verify the exact same order is returned without duplicating orders in MongoDB.
