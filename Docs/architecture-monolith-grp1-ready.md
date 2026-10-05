# Shopping Cart Application
## Monolithic Architecture — Core Architecture

**Status:** CLAUDE REVIEWED — MINOR CHANGES APPLIED  
**Stack:** Vue.js + Spring Boot 4.1.1 (Java 21) + MongoDB + Keycloak  
**Architecture:** Modular Monolith  
**Primary communication:** HTTP from Vue to one Spring Boot application; in-process typed module APIs inside the backend  
**Implementation constraint:** Approximately 2 hours  

---

# 1. Architecture Objective

The application is implemented as a **modular monolith**: one Spring Boot deployment contains the Product, Cart, Order, and Checkout modules while preserving strict logical ownership between them.

The architecture is intentionally designed as more than a single undifferentiated codebase. Each business module owns its rules, persistence access, and public application-facing capabilities. Cross-module access occurs only through explicitly exposed module APIs.

The architecture prioritizes:

- clear domain/module boundaries;
- one deployable backend application;
- one shared MongoDB deployment;
- strong checkout consistency;
- server-authoritative business data;
- secure authentication and authorization;
- checkout idempotency;
- practical testing and observability;
- simple local deployment.

The architecture does **not** require a runtime service boundary between Product, Cart, Order, and Checkout.

---

# 2. High-Level Architecture

```text
                         ┌─────────────────────┐
                         │      Vue.js UI      │
                         │                     │
                         │ Customer UI         │
                         │ Admin UI            │
                         │ Developer/Test UI   │
                         └──────────┬──────────┘
                                    │
                                    │ HTTPS / REST + JWT
                                    ▼
                         ┌──────────────────────────┐
                         │      Spring Boot         │
                         │     MODULAR MONOLITH     │
                         │                          │
                         │  ┌────────────────────┐  │
                         │  │ Security           │  │
                         │  ├────────────────────┤  │
                         │  │ Product            │  │
                         │  ├────────────────────┤  │
                         │  │ Cart               │  │
                         │  ├────────────────────┤  │
                         │  │ Order              │  │
                         │  ├────────────────────┤  │
                         │  │ Checkout           │  │
                         │  └────────────────────┘  │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │        MongoDB           │
                         │                         │
                         │ products                │
                         │ carts                   │
                         │ orders                  │
                         └─────────────────────────┘

                         ┌─────────────────────────┐
                         │       Keycloak          │
                         │   OIDC / JWT Identity   │
                         └─────────────────────────┘
```

There is one backend runtime boundary. Vue remains a separate client application and Keycloak remains an external identity provider.

There is no Spring Cloud Gateway in the baseline. Browser traffic terminates directly at the Spring Boot application.

---

# 3. Module Architecture

```text
backend/
├── security/
├── product/
├── cart/
├── order/
└── checkout/
```

Each business module follows the principle:

> **A module exposes business capabilities through a small public API and keeps persistence models, repositories, and internal services private.**

A package structure alone is not considered sufficient boundary enforcement. Architecture tests must verify the intended module dependency rules.

## 3.1 Allowed dependency direction

```text
Web/API
   │
   ├──────────────► Product API
   │
   ├──────────────► Cart API
   │
   ├──────────────► Order API
   │
   └──────────────► Checkout
                         │
                         ├──► Cart API
                         └──► Order API

Cart API ───────────────► Product API

Product ────────────────► no business-module dependency
Order ──────────────────► no Cart persistence access
```

The following are forbidden:

```text
Order → CartRepository                         ❌
Checkout → Cart Mongo document                 ❌
Product → OrderRepository                     ❌
Cart → orders collection                      ❌
Controller → another module's repository      ❌
```

Cross-module interaction must occur through explicit interfaces or use-case services. These module APIs must not expose MongoDB `ClientSession`, repositories, persistence entities, or other transaction implementation details. The Checkout module owns transaction demarcation; Cart and Order participate in the ambient Spring-managed MongoDB transaction and must not start separate transactions for the same checkout.

---

# 4. Product Module

The Product module is the sole owner of current product catalog truth.

### Responsibilities

- Create products
- Update products
- Delete/deactivate products
- Retrieve products
- Retrieve individual products
- Search/filter products if implemented
- Validate product information
- Maintain current product price
- Enforce product administration authorization

### Example public HTTP APIs

```http
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

### Internal module capability

Other modules may request product information through a narrow interface such as:

```java
ProductSnapshot getAvailableProduct(ProductId productId);
```

They must not access the Product repository directly.

---

# 5. Cart Module

The Cart module owns all user cart state.

### Responsibilities

- Retrieve authenticated user's cart
- Add item
- Update quantity
- Remove item
- Clear/finalize cart during checkout
- Calculate subtotal
- Enforce ownership
- Validate product existence when adding an item
- Store server-derived product information required by the cart
- Maintain cart revision information for concurrency control

### Example public HTTP APIs

```http
GET    /api/carts/me
POST   /api/carts/me/items
PUT    /api/carts/me/items/{productId}
DELETE /api/carts/me/items/{productId}
DELETE /api/carts/me
```

### Cart model

```text
Cart
├── id
├── userId
├── version
├── items[]
│   ├── productId
│   ├── productName
│   ├── unitPrice
│   └── quantity
└── updatedAt
```

`version` identifies the cart revision used by checkout. `updatedAt` records the latest modification time.

The baseline Cart model does not persist a checkout-state machine. Checkout is represented by one atomic database transaction, so intermediate checkout states are not required. A future asynchronous workflow may introduce explicit checkout states, but that is outside the baseline.

### Internal public capability

Checkout should consume a purpose-specific Cart API rather than a Cart repository or entity:

```java
CheckoutCart loadForCheckout(UserId userId);
void finalizeCheckout(UserId userId, CartVersion version);
```

The finalization operation must verify that the cart revision being finalized is the one used to construct the order.

---

# 6. Order Module

The Order module owns persisted orders and order retrieval.

### Responsibilities

- Create orders
- Retrieve authenticated user's orders
- Retrieve an individual order with ownership checks
- Preserve historical purchase information
- Store immutable product/price snapshots
- Enforce checkout idempotency through persistence constraints
- Maintain order lifecycle state

### Example public HTTP APIs

```http
POST /api/orders/checkout
GET  /api/orders
GET  /api/orders/{id}
```

The checkout endpoint is exposed through the Checkout workflow even though the resulting resource is an Order.

### Order model

```text
Order
├── id
├── userId
├── idempotencyKey
├── items[]
│   ├── productId
│   ├── productName
│   ├── unitPrice
│   └── quantity
├── totalAmount
├── status
├── createdAt
└── updatedAt
```

The order is an immutable historical snapshot of the checkout result. Later Product changes must not modify historical order values.

---

# 7. Checkout Module

Checkout is a first-class application workflow rather than a synonym for Order CRUD.

Its responsibility is to coordinate the business capabilities of Cart and Order while respecting their module boundaries.

```text
Checkout
   │
   ├── authenticate user
   ├── validate idempotency key
   ├── load cart revision through Cart API
   ├── reject empty/invalid cart
   ├── calculate total from server-owned cart values
   ├── create immutable Order through Order API
   ├── finalize the same Cart revision
   └── return/replay the resulting Order
```

Checkout must not directly access:

```text
CartRepository     ❌
CartDocument       ❌
OrderRepository    ❌
ProductRepository  ❌
```

It works through module capabilities.

---

# 8. Database Architecture

The monolith uses one MongoDB deployment with logically separated collections:

```text
MongoDB
├── products
├── carts
└── orders
```

Logical ownership remains strict:

| Collection | Owner |
|---|---|
| `products` | Product module |
| `carts` | Cart module |
| `orders` | Order module |

Physical co-location does **not** imply unrestricted persistence access.

The Order module does not query `carts` directly. The Cart module does not write `orders`. The Product module does not modify cart snapshots.

Architecture tests should verify these rules.

---

# 9. Product and Price Semantics

The Product module remains the authority for current catalog data.

When an item is added to a cart:

```text
Client
  ↓
Cart Module
  ↓
Product API
  ↓
Validate product + obtain server-owned product data
  ↓
Store cart snapshot
```

The cart retains the server-derived snapshot:

```text
productId
productName
unitPrice
quantity
```

For the baseline experiment, **the cart snapshot price is authoritative for checkout**.

This preserves the existing application business rule and avoids introducing checkout-time repricing as an unrelated product change. **Checkout does not call the Product module again during the baseline checkout transaction**; it uses the server-owned Cart snapshot that was created when the item was added.

A future version may introduce explicit price revalidation, but this is not part of the baseline architecture.

The frontend can never supply an authoritative price or order total.

---

# 10. Checkout Consistency Contract

Checkout has a clear business invariant:

> **A successful checkout creates exactly one order for the authenticated user and idempotency key and finalizes the exact cart revision used to construct that order.**

For the baseline architecture, checkout is implemented as a **single MongoDB multi-document transaction owned by the Checkout application service**.

The transaction is demarcated once at the Checkout use case (for example, a Spring-managed `@Transactional` method). Cart and Order module APIs participate in that same ambient transaction; they do **not** create independent transactions and they do **not** expose MongoDB `ClientSession` through their public APIs. Spring's transaction management binds the database session to the current application execution context.

Conceptually:

```text
BEGIN TRANSACTION   ← Checkout owns transaction demarcation
    │
    ├── load cart revision through Cart API
    │
    ├── validate cart
    │
    ├── create immutable order through Order API
    │
    └── finalize/clear same cart revision through Cart API
    │
COMMIT
```

All Cart and Order database operations above participate in the **same transaction context**. If any database operation fails before commit:

```text
ROLLBACK
   ↓
No partially created order
No finalized cart
```

No distributed compensation is required for the core checkout operation because Cart and Order persistence participate in the same application/database transaction.

### Transaction boundary rule

The transaction contains only local database work.

It must **not** contain future external operations such as:

- payment-provider calls;
- email delivery;
- remote inventory APIs;
- external message-broker calls.

Such operations belong after commit and require their own reliability mechanism.

### MongoDB deployment requirement

Transactional checkout requires a MongoDB deployment that supports multi-document transactions. Local development therefore uses a replica-set-capable MongoDB configuration rather than a standalone MongoDB process.

---

# 11. Checkout Concurrency

Cart modifications and checkout must not silently operate on different cart revisions.

The cart therefore carries a `version` value.

Conceptually:

```text
Cart version = 7
        │
        ├── Checkout reads version 7
        │
        └── Checkout finalizes version 7
```

If another operation changes the cart during the checkout workflow, the finalization must detect the revision mismatch rather than silently clearing a newer cart state.

The implementation may use MongoDB conditional updates or another optimistic-concurrency mechanism, but the architectural rule is:

> **Checkout must finalize the same cart revision from which the order snapshot was created.**

If the expected cart revision does not match at finalization, the transaction must abort and the API returns **409 Conflict** indicating that the cart changed during checkout. The server must not automatically retry the checkout transaction; the client must re-read the current cart and initiate a new checkout attempt.

---

# 12. Checkout Idempotency

Checkout remains protected by an idempotency key because duplicate requests can originate from the client even in a monolith.

Required database uniqueness:

```javascript
{ userId: 1, idempotencyKey: 1 }
```

with uniqueness enabled.

### Behavior

The idempotency check must not be implemented as a separate read followed by an insert outside the transaction. The **unique `(userId, idempotencyKey)` index is the authoritative race guard**.

```text
Request with key K
    ↓
Begin Checkout transaction
    ↓
Create order using K
    ↓
Unique index enforces one winner
    ↓
Finalize matching cart revision
    ↓
Commit
```

For a repeated request using the same authenticated user and key: 

```text
Existing committed order
    ↓
Return existing order

Concurrent duplicate key race
    ↓
Transactional attempt aborts
    ↓
Re-fetch the committed order for the same user + key
    ↓
Return the existing order rather than a generic 500
```

If a transaction aborts for another reason before an order commits, its writes are rolled back and the same idempotency key may be retried against the current cart state.

A key cannot be reused to represent a different logical checkout once an order for that user and key has been committed.

The unique index remains necessary because application-level check-then-insert logic is vulnerable to concurrent races.

---

# 13. Order Status

For the baseline synchronous checkout flow, successful completion produces:

```text
CONFIRMED
```

A failed transaction does **not** create a durable `FAILED` order merely to record an internal database failure. The transaction either commits the intended state or rolls back.

`PENDING`, `FAILED`, and `CANCELLED` are reserved for future workflows that genuinely require a staged or externally dependent lifecycle, such as payment processing. Their presence in future versions must not weaken the baseline transactional checkout invariant.

Therefore the baseline checkout path is:

```text
request
  ↓
transaction
  ├── success → CONFIRMED order
  └── failure → rollback; no partial order
```

---

# 14. API Gateway and Edge Architecture

There is no Spring Cloud Gateway.

```text
Vue
  │
  │ HTTPS / REST + JWT
  ▼
Spring Boot Monolith
```

Responsibilities previously associated with an application gateway are handled at the monolith edge or by deployment infrastructure:

| Concern | Location |
|---|---|
| Routing | Spring MVC / application routing |
| JWT authentication | Spring Security |
| Coarse RBAC | Spring Security filter chain |
| CORS | Spring Security / CORS configuration |
| Request ID | Servlet/application filter |
| Structured access logging | Application observability |
| Error normalization | Global exception handling / Problem Details |
| Rate limiting | Application/deployment edge if required |
| TLS termination | Reverse proxy/load balancer/application |

No internal gateway layer is recreated.

For the baseline security model, public product mutation routes (`POST`, `PUT`, `DELETE /api/products/**`) require `ROLE_ADMIN` through the application security filter chain. Product-module services may still enforce business-specific checks, but the route-level role requirement is centralized rather than duplicated.

---

# 15. Security Boundary

The Spring Boot application is the single API security boundary.

```text
Vue
  │
  │ OIDC Authorization Code + PKCE
  ▼
Keycloak
  │
  │ Access JWT
  ▼
Vue
  │
  │ Authorization: Bearer <JWT>
  ▼
Spring Security
  │
  ├── validate JWT
  ├── establish authenticated principal
  ├── map roles
  └── enforce route/use-case authorization
       │
       ├── Product
       ├── Cart
       ├── Order
       └── Checkout
```

There are no per-service resource-server boundaries or JWT forwarding steps.

Module-level authorization remains mandatory.

---

# 16. Resource Ownership

User identity is derived from the validated JWT `sub` claim.

User-specific operations use authenticated ownership semantics:

```http
GET    /api/carts/me
POST   /api/carts/me/items
PUT    /api/carts/me/items/{productId}
DELETE /api/carts/me/items/{productId}
GET    /api/orders
GET    /api/orders/{id}
```

For individual orders:

```text
authenticatedUser = JWT.sub
        ↓
load order
        ↓
owner matches?
   ├── yes → return order
   ├── admin → authorized administrative access
   └── no → reject without exposing unauthorized resource details
```

The removal of process boundaries does not remove resource ownership boundaries.

---

# 17. Resilience Model

The monolith no longer needs resilience mechanisms for internal HTTP calls.

### Removed from internal module communication

- Service discovery
- Service URLs
- Internal HTTP serialization/deserialization
- Internal JWT forwarding
- Service-to-service authentication
- Internal HTTP timeouts
- Internal HTTP retries
- Circuit breakers between Product/Cart/Order
- Downstream-503 handling for another application service
- Distributed checkout compensation caused by network failure
- Distributed tracing solely for module-to-module hops

### Retained at the application/data boundary

- Database timeout/error handling
- Connection-pool limits
- Input and payload limits
- Idempotency
- Concurrency protection
- Validation
- Structured error responses
- Request timeouts at the external HTTP boundary
- Operational monitoring

The monolith removes network failure modes; it does not remove failure handling altogether.

---

# 18. Observability

Distributed tracing is not required for the internal module call path.

The baseline retains:

- structured application logs;
- request/correlation ID;
- authenticated subject where safe;
- checkout/order identifier;
- safe idempotency identifier or hash;
- endpoint, status, latency, and error category;
- MongoDB/application health metrics;
- JVM and resource metrics where available.

Never log:

- access tokens;
- refresh tokens;
- passwords;
- client secrets;
- unnecessary personal information.

A request ID must allow a single browser request to be followed through Controller → Module → MongoDB-related application logs without requiring distributed tracing infrastructure.

---

# 19. Architecture Verification

The module boundaries are part of the architecture, not merely a naming convention.

The project should include automated architecture verification for rules such as:

```text
Product internal types cannot be imported by Cart/Order
Cart repositories cannot be imported by Order/Checkout
Order repositories cannot be imported by Cart
Controllers cannot directly access another module's repository
No cyclic module dependencies
```

Spring Modulith is the preferred candidate for this verification because it can verify module dependencies and exposed/internal package boundaries.

The architecture verification itself is a test-time concern and does not create a separate runtime service.

---

# 20. Configuration

Environment-specific values include:

```text
MONGODB_URI
KEYCLOAK_ISSUER_URI
KEYCLOAK_CLIENT_ID
KEYCLOAK_CLIENT_SECRET   # only where genuinely required
```

The monolith no longer requires:

```text
PRODUCT_SERVICE_URL
CART_SERVICE_URL
ORDER_SERVICE_URL
GATEWAY_URL
```

No deployment-specific hostname should be hard-coded into business logic.

---

# 21. Local Deployment

Baseline development topology:

```text
Docker Compose
├── MongoDB (replica-set capable)
└── Keycloak

Local JVM
└── Spring Boot Monolith

Local development server
└── Vue SPA
```

The monolith can later be packaged into one application container without changing its internal module topology.

---

# 22. Testing Implications

Testing follows architectural boundaries rather than old deployment boundaries.

### Unit tests

- cart quantity rules;
- total calculation;
- validation;
- authorization predicates;
- product validation.

### Module integration tests

- Product public API;
- Cart public API;
- Order public API;
- Checkout → Cart API interaction;
- Checkout → Order API interaction;
- forbidden cross-module access.

### Persistence integration tests

- MongoDB mappings;
- unique idempotency index;
- cart revision behavior;
- transactional checkout;
- rollback behavior;
- ownership-filtered queries.

### Security/controller tests

- 401/403/404/409 behavior;
- RBAC;
- IDOR attempts;
- forged identity fields;
- Problem Details responses.

### E2E

One golden path:

```text
Login
 ↓
Browse products
 ↓
Add to cart
 ↓
Update quantity
 ↓
Checkout
 ↓
Confirmation
 ↓
View order
```

Concurrent checkout and transaction failure tests are critical because the main consistency risk is now local concurrency rather than inter-service failure.

---

# 23. Project Structure

```text
project/
├── frontend/
├── backend/
│   └── src/main/java/.../
│       ├── security/
│       ├── product/
│       │   ├── api/
│       │   ├── internal/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   └── model/
│       ├── cart/
│       │   ├── api/
│       │   ├── internal/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   └── model/
│       ├── order/
│       │   ├── api/
│       │   ├── internal/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   └── model/
│       └── checkout/
├── infra/
│   └── docker-compose.yml
├── e2e/
└── README.md
```

`api/` represents the public module contract. `internal/` contains implementation details that other modules must not import.

---

# 24. Explicit Non-Goals

The baseline does not add unrelated business functionality merely because the architecture is monolithic.

Out of scope:

- Payment gateway
- Real inventory reservation
- Recommendation engine
- Full-text search infrastructure
- Distributed message broker
- Complex Saga framework
- Kubernetes
- Service mesh
- Distributed tracing platform
- Large-scale caching infrastructure
- Advanced analytics platform

Potential future capabilities such as price revalidation, inventory coordination, notifications, and application events may be evaluated separately if the project requirements justify them.

---

# 25. Architecture Decision Summary

| Concern | Monolithic decision |
|---|---|
| Backend deployment | One Spring Boot application (Spring Boot 4.1.1, Java 21) |
| Domain structure | Product, Cart, Order, Checkout modules |
| Module interaction | Typed in-process APIs |
| API Gateway | Removed |
| Internal REST | Removed |
| Service discovery | Removed |
| Service-to-service JWT | Removed |
| Database | One MongoDB deployment with products/carts/orders collections |
| Data ownership | Preserved logically per module |
| Checkout | Dedicated application workflow |
| Checkout consistency | MongoDB transaction |
| Cart concurrency | Cart version/revision |
| Idempotency | Unique `(userId, idempotencyKey)` |
| Baseline order state | CONFIRMED; future lifecycle states are out of scope |
| Authentication | Keycloak / OIDC / JWT |
| JWT validation | One application security boundary |
| RBAC | Retained at application/module level |
| Resource ownership | Retained |
| Product price authority | Product module; cart snapshot remains baseline checkout value |
| Distributed compensation | Removed from core checkout |
| Internal HTTP retries/timeouts | Removed |
| Observability | Structured logs + request correlation + metrics |
| Module verification | Automated architecture tests; Spring Modulith candidate |

---

# 26. Claude Review Reconciliation

The independent review found no structural architectural problems. The following clarifications are now explicit: (1) Checkout owns transaction demarcation and Cart/Order participate in the same Spring-managed MongoDB transaction without exposing `ClientSession`; (2) idempotency uniqueness is enforced inside the transaction and duplicate-key races resolve by re-fetching the committed order; (3) cart revision mismatch aborts checkout and returns 409 with no automatic retry; (4) baseline checkout does not re-read Product; (5) admin product mutation is protected at the application security boundary; (6) `checkoutState` is removed from the baseline model; and (7) failed transactions do not create a durable `FAILED` order.

# 27. Final Position

The monolithic architecture is a **modular monolith**, not an undifferentiated codebase.

It deliberately removes runtime/distributed machinery where a single process makes it unnecessary while retaining the business, security, and data-ownership boundaries that remain valuable within one application.

The core architectural properties are:

1. **One backend deployment.**
2. **Strict Product, Cart, Order, and Checkout module boundaries.**
3. **Typed in-process module APIs instead of internal HTTP.**
4. **Logical database ownership despite physical persistence consolidation.**
5. **Atomic checkout through a local MongoDB transaction.**
6. **Concurrency protection through explicit cart revisions.**
7. **Idempotent checkout enforced by a database uniqueness constraint.**
8. **Centralized authentication with module-level authorization.**
9. **Application-level observability instead of distributed tracing infrastructure.**
10. **No unrelated feature expansion simply because the architecture is monolithic.**

**Status: CLAUDE REVIEWED — MINOR CHANGES APPLIED; ARCHITECTURE IS READY TO PROCEED TO THE NEXT FILE.**
