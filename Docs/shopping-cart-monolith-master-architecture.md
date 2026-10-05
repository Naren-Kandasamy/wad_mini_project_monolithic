# Shopping Cart Application
## Monolithic Architecture — Master Architecture

**Project:** Shopping Cart Full-Stack Web Application  
**Stack:** Vue.js + Spring Boot + MongoDB + Keycloak  
**Architecture:** Modular Monolith  
**Status:** CONSOLIDATED FROM CLAUDE-REVIEWED GROUPS 1–3 — READY  
**Implementation constraint:** Approximately 2 hours for the academic implementation

---

# 1. Master Architecture Purpose

This document is the consolidated architecture for the **monolithic experiment** of the Shopping Cart application.

It combines the three independently finalized architecture groups:

- **Group 1 — Core Modular Monolith Architecture**
- **Group 2 — Security Architecture**
- **Group 3 — Testing & Production Readiness Architecture**

The master architecture is the single consolidated reference for the monolithic experiment.

The individual group documents remain the detailed references for their respective concerns, but this document defines how the complete system fits together.

The architecture is a **modular monolith**, not an undifferentiated codebase.

The backend is one Spring Boot deployment containing:

```text
Security
Product
Cart
Order
Checkout
```

These remain logically separated through explicit module APIs, owned persistence, and architecture rules.

---

# 2. Master Architectural Principles

The complete architecture follows these principles:

1. **One backend deployment.**
2. **Strict Product, Cart, Order, and Checkout module boundaries.**
3. **Typed in-process APIs instead of internal HTTP.**
4. **One MongoDB deployment with logical collection ownership.**
5. **Checkout is a dedicated application workflow.**
6. **Checkout uses one local MongoDB transaction.**
7. **Cart revisions protect against stale checkout state.**
8. **Idempotency is enforced by a database uniqueness constraint.**
9. **Keycloak remains the external identity provider.**
10. **JWT validation occurs at one application-level Spring Security boundary.**
11. **RBAC and resource ownership remain enforced server-side.**
12. **Client-controlled identity and business-security data are never authoritative.**
13. **Testing protects business invariants rather than chasing code coverage.**
14. **Architecture tests protect modular boundaries.**
15. **Observability is application-level rather than distributed-system infrastructure.**
16. **Deployment is intentionally simpler than the microservice experiment.**
17. **The architecture does not add infrastructure merely to appear more production-ready.**

---

# 3. High-Level System Architecture

```text
                           ┌──────────────────────┐
                           │       Keycloak       │
                           │    External OIDC     │
                           │   Users / Roles      │
                           │    JWT issuance      │
                           └──────────┬───────────┘
                                      │
                              OIDC / OAuth 2.0
                           Authorization Code + PKCE
                                      │
                                      ▼
┌───────────────────────────────────────────────────────────────┐
│                         Vue SPA                               │
│                                                               │
│ Product browsing                                              │
│ Cart management                                                │
│ Checkout                                                       │
│ Order history                                                  │
│ Admin UI                                                       │
└────────────────────────────┬──────────────────────────────────┘
                             │
                             │ HTTPS / REST
                             │ Authorization: Bearer <JWT>
                             ▼
┌───────────────────────────────────────────────────────────────┐
│                     SPRING BOOT MONOLITH                      │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ Spring Security                                          │  │
│  │ JWT validation / authentication / RBAC / CORS            │  │
│  └──────────────────────────┬──────────────────────────────┘  │
│                             │                                  │
│        ┌────────────────────┼───────────────────────────┐      │
│        │                    │                           │      │
│        ▼                    ▼                           ▼      │
│   ┌──────────┐        ┌──────────┐                ┌────────┐ │
│   │ Product  │        │   Cart   │                │ Order  │ │
│   │  Module  │        │  Module  │                │ Module │ │
│   └──────────┘        └────┬─────┘                └───┬────┘ │
│                             │                          │      │
│                             └──────────┬───────────────┘      │
│                                        ▼                      │
│                                  ┌──────────┐                │
│                                  │ Checkout │                │
│                                  │  Module  │                │
│                                  └──────────┘                │
│                                                               │
│  Error Handling / Logging / Correlation / Metrics / Health    │
└───────────────────────────────┬───────────────────────────────┘
                                │
                                ▼
                     ┌──────────────────────┐
                     │       MongoDB        │
                     │                      │
                     │ products             │
                     │ carts                │
                     │ orders               │
                     └──────────────────────┘
```

There is one backend runtime boundary.

Vue remains a separate client application.

Keycloak remains an external identity provider.

There is no Spring Cloud Gateway in the monolithic baseline.

---

# 4. What the Monolith Removes

The following microservice runtime mechanisms are intentionally removed:

```text
Spring Cloud Gateway
Service discovery
Service registry
Product runtime service
Cart runtime service
Order runtime service
Inter-service REST
Internal service URLs
Service-to-service JWT forwarding
Service-to-service authentication
Client-credentials service calls
mTLS between backend modules
Internal HTTP timeouts
Internal HTTP retries
Circuit breakers between modules
Distributed compensation for internal checkout calls
Distributed tracing for internal module hops
```

They are unnecessary because Product, Cart, Order, and Checkout execute inside one Spring Boot process.

This does **not** remove business, security, persistence, or ownership boundaries.

---

# 5. Runtime Components

| Component | Responsibility |
|---|---|
| Vue SPA | User interface and browser interaction |
| Keycloak | External identity provider and JWT issuer |
| Spring Boot Monolith | All backend modules, API, security, business logic, persistence orchestration, observability |
| MongoDB | Persistent products, carts, and orders |
| Reverse proxy/load balancer | Optional deployment edge and TLS termination |
| CI pipeline | Automated build and verification |
| Playwright | Browser-level end-to-end verification |

---

# 6. Backend Module Architecture

```text
backend/
├── security/
├── product/
│   ├── api/
│   └── internal/
├── cart/
│   ├── api/
│   └── internal/
├── order/
│   ├── api/
│   └── internal/
├── checkout/
│   ├── api/
│   └── internal/
└── shared/
```

The exact package structure may vary.

The architectural rule remains:

> A module exposes business capabilities through a small public API while keeping persistence models, repositories, and implementation details private.

A package naming convention by itself is not enough.

Architecture tests must enforce the intended dependency graph.

---

# 7. Module Dependency Rules

Allowed high-level flow:

```text
Web/API
   │
   ├──────────────► Product API
   │
   ├──────────────► Cart API
   │
   ├──────────────► Order API
   │
   └──────────────► Checkout API
                         │
                         ├──► Cart API
                         └──► Order API

Cart API ───────────────► Product API
```

Forbidden:

```text
Order → CartRepository
Checkout → CartRepository
Checkout → OrderRepository
Product → OrderRepository
Product → CartRepository
Controller → another module's repository
Module → another module's internal package
```

Module APIs must not expose:

- MongoDB repositories;
- persistence entities;
- `ClientSession`;
- transaction implementation details.

---

# 8. Product Module

The Product module is the sole owner of current catalog truth.

## Responsibilities

- Create products.
- Update products.
- Delete/deactivate products.
- Retrieve products.
- Retrieve individual products.
- Search/filter products if implemented.
- Validate product information.
- Maintain current product price.
- Enforce product-administration authorization.

## Public APIs

```http
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

Product mutation requires `ROLE_ADMIN`.

## Internal capability

Other modules may request product information through a narrow typed API such as:

```java
ProductSnapshot getAvailableProduct(ProductId productId);
```

Other modules must not access the Product repository directly.

---

# 9. Cart Module

The Cart module owns all user cart state.

## Responsibilities

- Retrieve authenticated user's cart.
- Add items.
- Update quantity.
- Remove items.
- Clear/finalize cart during checkout.
- Calculate subtotal.
- Enforce ownership.
- Validate product existence when adding an item.
- Store server-derived product information.
- Maintain cart revision information.

## Public APIs

```http
GET    /api/carts/me
POST   /api/carts/me/items
PUT    /api/carts/me/items/{productId}
DELETE /api/carts/me/items/{productId}
DELETE /api/carts/me
```

## Cart model

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

`version` identifies the cart revision used by checkout.

`updatedAt` records the latest modification time.

The baseline cart does not persist a checkout state machine.

## Checkout-facing Cart API

```java
CheckoutCart loadForCheckout(UserId userId);

void finalizeCheckout(UserId userId, CartVersion version);
```

Finalization must verify that the revision being finalized is the same revision used to construct the order.

---

# 10. Order Module

The Order module owns persisted orders and order retrieval.

## Responsibilities

- Create orders.
- Retrieve authenticated user's orders.
- Retrieve individual orders with ownership checks.
- Preserve historical purchase information.
- Store immutable product/price snapshots.
- Maintain idempotency persistence constraints.
- Maintain order lifecycle state.

## Public APIs

```http
POST /api/orders/checkout
GET  /api/orders
GET  /api/orders/{id}
```

The checkout endpoint is a Checkout workflow even though its result is an Order.

## Order model

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

The order is an immutable historical snapshot.

Later Product changes do not modify existing order values.

---

# 11. Checkout Module

Checkout is a first-class application workflow.

It coordinates Cart and Order through public module APIs.

```text
Checkout
   │
   ├── authenticate user
   ├── validate idempotency key
   ├── load cart revision
   ├── reject empty/invalid cart
   ├── calculate total from server-owned cart values
   ├── create immutable Order
   ├── finalize the same Cart revision
   └── return/replay resulting Order
```

Checkout does not directly access:

```text
CartRepository
CartDocument
OrderRepository
ProductRepository
```

---

# 12. Data Architecture

One MongoDB deployment is used.

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

Physical co-location does not imply unrestricted persistence access.

---

# 13. Critical Database Indexes

At minimum:

```text
carts
    unique(userId)

orders
    unique(userId, idempotencyKey)

products
    unique(id)
```

Additional indexes may support actual access patterns, for example:

```text
orders.userId
orders.createdAt
```

Index creation must be reproducible across environments.

---

# 14. Product and Price Semantics

Product remains the authority for current catalog information.

When an item is added:

```text
Client
  ↓
Cart Module
  ↓
Product API
  ↓
Validate product + obtain server-owned data
  ↓
Store cart snapshot
```

The cart stores:

```text
productId
productName
unitPrice
quantity
```

For the baseline experiment:

> **The Cart snapshot price is authoritative for checkout.**

Checkout does not re-read Product during the baseline checkout transaction.

The frontend can never supply an authoritative price or order total.

A future version may add explicit price revalidation, but it is not part of this baseline.

---

# 15. Checkout Consistency Model

The central checkout invariant is:

> **A successful checkout creates exactly one order for the authenticated user and idempotency key and finalizes the exact cart revision used to construct that order.**

Checkout is implemented as a **single MongoDB multi-document transaction** owned by the Checkout application service.

```text
BEGIN TRANSACTION
    │
    ├── load cart revision through Cart API
    ├── validate cart
    ├── create immutable order through Order API
    └── finalize/clear same cart revision through Cart API
    │
COMMIT
```

Checkout owns transaction demarcation.

Cart and Order participate in the same ambient Spring-managed transaction.

They do not start separate transactions.

They do not expose MongoDB `ClientSession`.

---

# 16. Transaction Boundary Rules

The checkout transaction contains only local database work.

It must not contain:

- payment-provider calls;
- email delivery;
- remote inventory calls;
- message-broker calls;
- other future external operations.

If a database operation fails before commit:

```text
ROLLBACK
   ↓
No partial order
No finalized cart
```

No distributed compensation is required for the core checkout flow because Cart and Order persistence participate in the same MongoDB transaction.

---

# 17. MongoDB Transaction Deployment Requirement

Checkout requires MongoDB multi-document transaction support.

A transaction-capable replica-set configuration is therefore required.

A **single-node replica set is sufficient** for development and test environments.

A multi-node cluster is not required for the two-hour implementation.

Conceptually:

```text
Spring Boot
     │
     │ Mongo transaction
     ▼
MongoDB replica set
```

A standalone MongoDB process must not be presented as equivalent to the transaction-capable checkout environment.

---

# 18. Checkout Concurrency

The Cart contains a `version`.

Example:

```text
Cart version = 7
       │
       ├── Checkout reads version 7
       │
       └── Checkout finalizes version 7
```

If another operation changes the cart:

```text
Request A
    loads version 7

Request B
    modifies cart
        ↓
    version 8

Request A
    attempts checkout
        ↓
    version mismatch
        ↓
    transaction abort
        ↓
    409 Conflict
```

The server does not silently merge states.

The server does not automatically retry the transaction.

The client must re-read the current cart and start a new checkout attempt.

---

# 19. Idempotency Architecture

Checkout requires an `Idempotency-Key`.

The authoritative database constraint is:

```javascript
{ userId: 1, idempotencyKey: 1 }
```

with uniqueness enabled.

The check is not implemented as a non-transactional "read then insert" sequence.

## Normal request

```text
Request with key K
    ↓
Begin transaction
    ↓
Create order using K
    ↓
Finalize matching cart
    ↓
Commit
```

## Repeated committed request

```text
Same user + same K
    ↓
Existing committed order
    ↓
Return existing order
```

## Concurrent race

```text
Same user + K
    ↓
Two transactions
    ↓
Unique index selects one winner
    ↓
Losing transactional attempt aborts
    ↓
Re-fetch committed order
    ↓
Return existing order
```

A duplicate-key race must not become a generic `500`.

If a transaction aborts for another reason before an order commits, the same key may be retried against the current cart state.

Once an order for that user and key has committed, the key cannot represent a different logical checkout.

---

# 20. Order Lifecycle

For the synchronous baseline checkout:

```text
CONFIRMED
```

is the successful terminal state.

A failed transaction does not create a durable `FAILED` order merely to record an internal database failure.

Future states such as:

```text
PENDING
FAILED
CANCELLED
```

are reserved for genuinely staged or externally dependent workflows.

They are not part of the baseline checkout lifecycle.

---

# 21. External API Surface

## Product

```http
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

## Cart

```http
GET    /api/carts/me
POST   /api/carts/me/items
PUT    /api/carts/me/items/{productId}
DELETE /api/carts/me/items/{productId}
DELETE /api/carts/me
```

## Orders

```http
POST   /api/orders/checkout
GET    /api/orders
GET    /api/orders/{id}
GET    /api/admin/orders/{orderId}
```

`GET /api/admin/orders/{orderId}` is restricted to `ROLE_ADMIN`.

---

# 22. Security Architecture

Keycloak remains the external identity provider.

```text
Keycloak
   ↓
OIDC Authorization Code + PKCE
   ↓
Vue SPA
   ↓
JWT
   ↓
Spring Security
   ↓
Product / Cart / Order / Checkout
```

There is one application-level authentication boundary.

There are no per-service JWT validation boundaries.

There is no JWT forwarding between modules.

---

# 23. Authentication Decisions

| Concern | Decision |
|---|---|
| Identity provider | Keycloak |
| Protocol | OIDC / OAuth 2.0 |
| Browser flow | Authorization Code + PKCE |
| SPA type | Public client |
| API credential | JWT access token |
| Access token storage | In-memory |
| `localStorage` access token | Not used |
| Refresh endpoint | Not custom-built |
| JWT validation | One Spring Security boundary |

The API must not use the ID token as its authorization credential.

---

# 24. JWT Validation

Spring Security validates:

1. JWT signature;
2. issuer;
3. expiration/timestamps;
4. audience.

Expected audience:

```text
shopping-cart-api
```

Keycloak must be explicitly configured to emit that audience, for example with an Audience Mapper/client scope.

The deployed realm must be verified rather than assuming the claim exists.

---

# 25. JWT Role Mapping

Keycloak roles:

```text
USER
ADMIN
DEVELOPER
```

They are extracted from the nested:

```text
realm_access.roles
```

claim using a custom `Converter<Jwt, Collection<GrantedAuthority>>` or equivalent converter.

Conceptually:

```text
Keycloak role
    ↓
realm_access.roles
    ↓
JWT converter
    ↓
ROLE_USER / ROLE_ADMIN / ROLE_DEVELOPER
```

A dotted claim-name configuration alone must not be assumed to extract the nested Keycloak structure correctly.

---

# 26. Authorization Model

Authorization is layered:

```text
Authentication
    ↓
Route-level authorization
    ↓
Module/use-case authorization
    ↓
Resource ownership
    ↓
Business validation
```

Backend authorization is authoritative.

Frontend route guards and role-aware UI are usability features, not security boundaries.

---

# 27. Resource Ownership

Identity is derived from:

```text
JWT.sub
```

The backend never trusts:

```text
request body userId
query parameter userId
X-User-Id
X-User-Role
frontend route state
```

User-owned endpoints use `/me` where appropriate.

Examples:

```http
GET    /api/carts/me
POST   /api/carts/me/items
PUT    /api/carts/me/items/{productId}
DELETE /api/carts/me/items/{productId}
GET    /api/orders
GET    /api/orders/{id}
```

For a normal user, an order belonging to another user is returned as:

```text
404 Not Found
```

rather than exposing unauthorized resource existence.

---

# 28. Administrative Authorization

`ROLE_ADMIN` is required for product mutation:

```http
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

`ROLE_ADMIN` is also required for:

```http
GET /api/admin/orders/{orderId}
```

Administrative access does not automatically grant unrestricted access to every resource.

---

# 29. Frontend Token Handling

Access tokens remain in memory.

Do not store them in:

```text
localStorage
```

If refresh tokens are used, the chosen OIDC client/provider library's browser storage behavior must be explicitly inspected.

Persistent storage such as `localStorage` or other long-lived browser storage must not be enabled silently.

Any required persistence trade-off must be documented as an explicit security decision.

---

# 30. CORS and Edge

There is no Spring Cloud Gateway.

Responsibilities are handled by Spring Boot/security or deployment infrastructure.

| Concern | Location |
|---|---|
| JWT validation | Spring Security |
| RBAC | Spring Security + module/use-case layer |
| CORS | Spring Security / Spring Web |
| Request ID | Servlet/application filter |
| Error normalization | Global exception handling |
| Logging | Application observability |
| Rate limiting | Optional application/deployment edge |
| TLS | Reverse proxy/load balancer/application |

Authenticated production deployments must use explicit origins.

Do not use:

```text
Access-Control-Allow-Origin: *
```

for authenticated production traffic.

---

# 31. CSRF Model

The baseline API uses:

```http
Authorization: Bearer <JWT>
```

rather than browser-managed authentication cookies.

Traditional cookie-based CSRF protection is therefore not part of the baseline API model.

If cookie/session authentication is introduced later, this decision must be revisited.

---

# 32. Security Logging

Security/operational logs may include:

```text
timestamp
level
requestId
endpoint
status
authenticated subject where appropriate
authorization outcome
operation
```

Never log:

```text
access tokens
refresh tokens
passwords
client secrets
Authorization headers
unnecessary sensitive personal information
```

---

# 33. Threat Model

Primary risks:

| Threat | Mitigation |
|---|---|
| Invalid token | JWT signature/issuer/expiry/audience validation |
| Missing auth | Spring Security |
| Privilege escalation | RBAC |
| IDOR | JWT-sub ownership checks |
| Forged identity headers | Ignore client-controlled identity |
| Client-modified price | Server-authoritative data |
| Duplicate checkout | Unique idempotency constraint |
| Module boundary erosion | Architecture tests |
| Exposed diagnostics | Restricted Actuator |
| Credential leakage | Environment configuration + safe logging |
| Shared-process blast radius | Least privilege + testing + monitoring |

---

# 34. Error Architecture

All backend errors use a common Problem Details representation.

Conceptually:

```json
{
  "type": "https://example.com/problems/cart-version-conflict",
  "title": "Cart changed during checkout",
  "status": 409,
  "detail": "The cart was modified after checkout started.",
  "instance": "/api/orders/checkout"
}
```

Primary mapping:

```text
400 → validation/request error
401 → authentication failure
403 → authorization failure
404 → resource absent/not disclosed
409 → business/state conflict
500 → unexpected server failure
```

The browser never receives:

- stack traces;
- MongoDB internals;
- JWT contents;
- secrets;
- filesystem paths;
- internal implementation details.

---

# 35. Observability Architecture

Distributed tracing is not required.

The baseline uses:

```text
Structured logs
      +
Request/correlation ID
      +
Micrometer metrics
      +
Health/readiness
      +
Problem Details
```

A request ID allows one browser request to be followed through:

```text
Controller
   ↓
Module
   ↓
MongoDB-related application logs
```

without distributed tracing infrastructure.

---

# 36. Correlation IDs

A servlet/application filter generates or accepts a request ID.

Conceptually:

```text
HTTP Request
    ↓
Correlation Filter
    ↓
Security
    ↓
Product / Cart / Order / Checkout
    ↓
Logs
```

The response may include:

```text
X-Request-Id: <request-id>
```

---

# 37. Metrics

Spring Boot Actuator + Micrometer provide the baseline.

Important metrics include:

```text
HTTP request count
HTTP latency
HTTP error rate
authentication failures
authorization failures
checkout success
checkout conflicts
checkout failures
idempotency replays
MongoDB failures
```

Prometheus/Grafana deployment is optional.

---

# 38. Health and Readiness

Expose:

```http
/actuator/health
```

The operational model distinguishes:

```text
Liveness
Readiness
```

### Liveness

The application process is alive.

### Readiness

The application is prepared to serve normal requests and required dependencies are available.

Health endpoints must not expose:

- environment variables;
- secrets;
- database credentials;
- unrestricted configuration;
- sensitive diagnostic information.

---

# 39. Configuration Architecture

Environment-dependent configuration is externalized.

Examples:

```text
MONGODB_URI
KEYCLOAK_ISSUER_URI
KEYCLOAK_CLIENT_ID
ALLOWED_ORIGINS
LOG_LEVEL
SERVER_PORT
```

Profiles:

```text
dev
test
prod
```

The `prod` profile must not enable development authentication shortcuts.

Critical configuration should fail fast rather than silently falling back to insecure defaults.

---

# 40. Development Environment

Recommended local topology:

```text
Developer Machine
│
├── Vue Dev Server
├── Spring Boot Monolith
│
└── Docker Compose
    ├── MongoDB
    └── Keycloak
```

MongoDB must be configured as a **single-node replica set**, for example using:

```text
--replSet rs0
```

and initialized with:

```text
rs.initiate()
```

or an equivalent Compose startup mechanism.

This ensures local checkout exercises the same transaction semantics as the architecture.

---

# 41. Test Environment

The test environment isolates:

```text
Application
MongoDB
Keycloak/test identity configuration
Test data
```

The MongoDB integration environment uses the same single-node replica-set configuration as development.

Tests generate/reset their own data.

They do not depend on a developer's existing database state.

---

# 42. Production Deployment

A simple production topology is:

```text
                    Internet
                       │
                      HTTPS
                       │
                       ▼
                Reverse Proxy /
                 Load Balancer
                       │
                       ▼
              Spring Boot Monolith
                       │
                 ┌─────┴─────┐
                 │           │
                 ▼           ▼
              MongoDB     Keycloak
```

The Spring Boot application is one deployable artifact/container.

MongoDB uses a production-capable transaction-supporting deployment.

Keycloak remains external to the application process.

---

# 43. Horizontal Scaling

The monolith can scale horizontally:

```text
             Load Balancer
              /    |    \
             ▼     ▼     ▼
           App 1 App 2 App 3
             \     |     /
              \    |    /
                MongoDB
```

Persistent business state remains in MongoDB.

Scaling is coarse-grained:

> Product, Cart, Order, and Checkout scale together.

Independent module scaling is intentionally not provided.

---

# 44. Deployment Trade-Offs

### Advantages

- One application to deploy.
- One security boundary.
- No service discovery.
- No internal network dependencies.
- Simpler local development.
- Simpler observability.
- Atomic Cart/Order transaction possible.
- Easier end-to-end verification.

### Trade-offs

- One process contains all modules.
- A major application failure affects all modules.
- Scaling is coarse-grained.
- Releases are coupled.
- Module boundaries require architectural enforcement.

These are accepted characteristics of the modular-monolith experiment.

---

# 45. Resilience Model

Internal module communication is in-process.

Therefore there are no internal:

```text
HTTP timeouts
HTTP retries
circuit breakers
service discovery
503 handling
network serialization
```

Retained resilience concerns include:

```text
database errors
connection-pool limits
input/payload limits
idempotency
concurrency control
validation
HTTP request timeouts
operational monitoring
```

The monolith removes network failure modes, not failure handling altogether.

---

# 46. Failure Behavior

## MongoDB unavailable

Expected:

```text
Database operation fails
    ↓
Safe error response
    +
Log/metric
    +
Readiness reflects dependency problem
```

No successful order is fabricated.

## Keycloak unavailable

Existing valid access-token processing may continue according to normal JWT validation behavior while required signing metadata remains available.

New authentication or metadata acquisition may fail.

Production must not fall back to insecure local authentication.

## Application process crashes

Persistent MongoDB state remains.

Clients retry according to API semantics.

Checkout idempotency prevents duplicate orders on retry with the same key.

---

# 47. Graceful Shutdown

Conceptually:

```text
SIGTERM
   ↓
Stop accepting new requests
   ↓
Complete in-flight requests
   ↓
Close resources
   ↓
Terminate process
```

Because all modules share one process, graceful shutdown affects the complete application.

---

# 48. Backup and Recovery

Production MongoDB requires a backup strategy.

The application does not implement backups itself.

The production environment should provide:

```text
Scheduled backups
Retention policy
Restore procedure
```

The authoritative state includes:

```text
products
carts
orders
```

Disaster-recovery automation is documented rather than implemented for the academic baseline.

---

# 49. Caching

No mandatory application cache is part of the baseline.

A future local cache may be used for relatively static product reads.

Caching must never become the authoritative source for checkout price correctness.

---

# 50. Testing Architecture

The test strategy has these layers:

```text
                    ┌──────────────────┐
                    │   E2E / Browser  │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │ Application/API  │
                    │ Integration      │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │ Module Tests     │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │ Unit Tests       │
                    └──────────────────┘

                    + Architecture Tests
                    + Security Tests
                    + Persistence Tests
                    + Concurrency Tests
```

The highest-value verification targets:

```text
Business invariants
Security invariants
Transaction behavior
Concurrency
Idempotency
Module boundaries
API semantics
End-to-end user flow
```

---

# 51. Unit Testing

Primary unit-test candidates:

```text
price calculations
order totals
quantity rules
validation
mapping
authorization predicates
snapshot creation
idempotency decision logic
```

Tools:

```text
JUnit 5
Mockito
Spring Boot test support
```

Unit tests do not attempt to emulate MongoDB transaction semantics.

---

# 52. Module Testing

## Product

```text
valid creation
invalid creation
read existing product
read missing product
admin mutation
non-admin mutation
```

## Cart

```text
load/create
add item
increase quantity
decrease quantity
remove item
clear
ownership
version updates
invalid product
```

## Order

```text
create order
calculate total
immutable snapshot
ownership
find order
idempotency record
```

## Checkout

```text
successful checkout
empty cart
stale cart version
duplicate idempotency key
concurrent idempotency requests
transaction rollback
cart finalization
```

---

# 53. Architecture Testing

Architecture tests must verify:

```text
No controller accesses another module's repository
No module accesses another module's internal package
No circular module dependency
Checkout accesses Cart through Cart API
Checkout accesses Order through Order API
Persistence remains inside owning module
```

Possible tools:

```text
ArchUnit
Spring Modulith verification
```

Only one needs to be adopted.

---

# 54. Persistence and Transaction Testing

Use a real MongoDB-compatible integration environment.

Mandatory persistence tests include:

```text
unique cart per user
unique idempotency key per user
transaction commit
transaction rollback
cart version persistence
order persistence
cart finalization
concurrent write behavior
```

Mocking cannot reliably prove database uniqueness or transaction semantics.

---

# 55. Checkout Concurrency Tests

### Cart version conflict

```text
Request A loads version 7
Request B changes cart → version 8
Request A attempts checkout
        ↓
409 Conflict
```

### Same idempotency key

```text
User + K
   ↓
Request A + Request B concurrently
   ↓
One transaction wins
   ↓
Other re-reads committed order
   ↓
One logical order
```

### Different users

```text
User A + K
User B + K
```

These are independent and may create separate orders.

---

# 56. Security Testing

Required authentication tests:

```text
missing JWT        → 401
malformed JWT      → 401
expired JWT        → 401
wrong issuer       → 401
wrong audience     → 401
invalid signature  → 401
```

Required authorization tests:

```text
USER → admin product mutation   → 403
USER → admin order lookup       → 403
ADMIN → admin order lookup     → success
```

Required ownership tests:

```text
User A → User A cart             → success
User A → User B cart             → rejected
User A → User A order            → success
User A → User B order            → 404
```

Also verify:

```text
forged X-User-Id
forged X-User-Role
client-supplied userId mismatch
modified client-side price
```

---

# 57. API Contract Testing

The external API is tested as one coherent contract.

Test categories:

```text
Authentication
Authorization
Validation
Ownership
Success behavior
Not-found behavior
Conflict behavior
Unexpected error behavior
```

OpenAPI may document the API contract.

Traditional service-to-service contract testing is not required because there are no independent backend service APIs.

---

# 58. Frontend E2E

The primary Playwright path is:

```text
Authenticate
   ↓
Browse products
   ↓
Add product
   ↓
Update quantity
   ↓
Checkout
   ↓
Order confirmation
   ↓
Cart empty/finalized
```

This validates the complete path:

```text
Browser
  ↓
Vue
  ↓
Keycloak
  ↓
Spring Boot
  ↓
MongoDB
```

The golden path has greater priority than exhaustive frontend component coverage.

---

# 59. Production Verification

After deployment, perform a smoke path:

```text
GET /actuator/health
        ↓
Authenticate
        ↓
GET /api/products
        ↓
GET /api/carts/me
        ↓
Checkout
        ↓
GET /api/orders
```

The purpose is to validate the deployed external path rather than merely the build artifact.

---

# 60. CI Architecture

Recommended steady-state pipeline (see §65 for the subset required within the two-hour implementation):

```text
Commit
  ↓
Compile
  ↓
Unit tests
  ↓
Module tests
  ↓
Architecture tests
  ↓
Security tests
  ↓
Mongo integration tests
  ↓
Transaction/concurrency tests
  ↓
Frontend build
  ↓
Playwright E2E
  ↓
Build/package
```

Critical failures stop the pipeline.

---

# 61. CI Test Tiers

## Pull Request

```text
compile
unit
module
architecture
security
```

## Main Branch

Add:

```text
MongoDB integration
transaction tests
concurrency tests
frontend build
Playwright golden path
```

## Release

Add:

```text
production configuration checks
container build
smoke tests
dependency/security scan where available
```

---

# 62. Docker Strategy

## Local development

Docker Compose may run:

```text
MongoDB
Keycloak
```

MongoDB is configured as a single-node replica set per §40/§41 — a standalone MongoDB container does not support the required checkout transactions.

Spring Boot and Vue may run locally.

## Integration environment

A complete containerized environment may run:

```text
Vue
Spring Boot
MongoDB
Keycloak
```

MongoDB is configured as a single-node replica set per §40/§41 — a standalone MongoDB container does not support the required checkout transactions.

## Production

One Spring Boot application artifact/container is deployed.

The monolith does not require separate Product, Cart, and Order containers.

---

# 63. Container Health

The application should expose:

```text
liveness
readiness
```

The deployment platform should route traffic only to ready instances.

---

# 64. Dependency Management

Production-minded dependency management includes:

- keeping Spring Boot/Spring Security versions aligned;
- removing unused dependencies;
- checking dependency vulnerabilities where available;
- avoiding duplicate libraries when existing Spring functionality is sufficient.

No elaborate supply-chain platform is required for the baseline.

---

# 65. Two-Hour Implementation Scope

## Must implement

### Core

- Spring Boot modular monolith.
- Product module.
- Cart module.
- Order module.
- Checkout module.
- MongoDB.
- Transaction-capable MongoDB configuration.

### Security

- Keycloak.
- OIDC Authorization Code + PKCE.
- JWT validation.
- `USER`, `ADMIN`, `DEVELOPER`.
- RBAC.
- Ownership checks.
- `/me` APIs where applicable.
- Explicit CORS.

### Correctness

- Cart version.
- MongoDB checkout transaction.
- `(userId, idempotencyKey)` unique index.
- Duplicate-key race handling.
- Problem Details.

### Verification

- Critical JUnit tests.
- Security tests.
- Persistence/transaction tests.
- One Playwright golden path.
- Health endpoint.
- Request/correlation ID.
- Structured logging.

---

# 66. Implement If Time Permits

- Architecture tests with ArchUnit or Spring Modulith.
- Additional concurrency tests.
- Metrics verification.
- Full containerized application build.
- Dependency vulnerability scanning.
- Additional Playwright negative scenarios.
- Detailed security audit logging.
- CSP hardening.
- Keycloak/Testcontainers integration verification.

---

# 67. Document Only / Future Work

```text
Kubernetes
Helm
Prometheus/Grafana deployment
Centralized log platform
Distributed tracing
Service mesh
Message broker
mTLS
OAuth2 client-credentials service identities
Token revocation infrastructure
Multi-region deployment
Blue/green deployment
Canary deployment
Advanced disaster recovery automation
Advanced SIEM/fraud infrastructure
```

---

# 68. Explicit Non-Goals

The baseline does not add unrelated business functionality merely because the system is monolithic.

Out of scope:

- payment gateway;
- real inventory reservation;
- recommendation engine;
- full-text search infrastructure;
- distributed message broker;
- complex Saga framework;
- Kubernetes;
- service mesh;
- distributed tracing platform;
- large-scale caching infrastructure;
- advanced analytics platform;
- multi-vendor seller security.

Potential future capabilities such as price revalidation, inventory coordination, notifications, and application events may be evaluated separately if project requirements justify them.

---

# 69. Key Operational Risks

## Single-process blast radius

A serious application failure can affect all modules.

Mitigation:

- automated regression testing;
- health/readiness;
- deployment rollback capability;
- strict module boundaries.

## Module boundary erosion

Developers can bypass APIs because everything runs in one process.

Mitigation:

- architecture tests;
- explicit `api`/`internal` packages;
- repository ownership;
- code review.

## Coarse scaling

All modules scale together.

Accepted for current workload.

## Shared database temptation

Developers may bypass collection ownership.

Mitigation:

- logical collection ownership;
- typed module APIs;
- architecture tests.

## Transaction environment mismatch

Development/test MongoDB may not support the required transactions.

Mitigation:

- single-node replica-set configuration;
- integration tests against the same model;
- explicit deployment documentation.

---

# 70. Master Golden Path

The primary end-to-end business journey is:

```text
User opens Vue application
       ↓
Authenticate through Keycloak
       ↓
Spring Security validates JWT
       ↓
Browse Product catalog
       ↓
Add product to Cart
       ↓
Cart validates product through Product API
       ↓
Server-owned price snapshot stored
       ↓
Update quantity
       ↓
Checkout with Idempotency-Key
       ↓
Checkout transaction starts
       ↓
Cart revision validated
       ↓
Order snapshot created
       ↓
Matching cart revision finalized
       ↓
Transaction commits
       ↓
CONFIRMED order returned
       ↓
Confirmation shown
       ↓
User views order
```

Critical failure paths include:

```text
Invalid JWT
    → 401

Insufficient role
    → 403

Unauthorized resource ownership
    → rejected / 404

Empty cart
    → 409

Stale cart version
    → transaction abort → 409

Duplicate idempotency key
    → existing committed order returned

Unexpected database failure
    → transaction rollback / safe 500
```

---

# 71. Master Security Principle

The complete architecture follows:

> **Authenticate once at the application boundary, authorize at the protected use case/resource owner, derive identity from the validated security context, preserve module boundaries, and never trust client-controlled identity or business-security data.**

---

# 72. Master Data-Consistency Principle

The complete checkout model follows:

> **The Cart and Order modules retain logical ownership, while Checkout coordinates them inside one MongoDB transaction and enforces idempotency and cart-version consistency.**

This is the central consistency advantage of the modular-monolith experiment over the original distributed checkout model.

---

# 73. Master Observability Principle

The observability model is:

```text
Logs
  +
Correlation IDs
  +
Metrics
  +
Health/Readiness
  +
Problem Details
```

This is sufficient for a single-process architecture.

Distributed tracing is not a baseline requirement.

---

# 74. Master Deployment Principle

The deployment model is:

```text
Vue
  +
Spring Boot Monolith
  +
MongoDB
  +
Keycloak
```

with an optional reverse proxy/load balancer at the edge.

There are no separate backend service containers in the monolithic experiment.

---

# 75. Master Testing Principle

Testing protects the architecture in this order:

```text
Business invariants
      ↓
Security invariants
      ↓
Persistence/transaction semantics
      ↓
Concurrency/idempotency
      ↓
Module boundaries
      ↓
API contract
      ↓
E2E journey
      ↓
UI details
```

The goal is not maximum test count.

The goal is maximum protection of the highest-risk behaviors.

---

# 76. Master Architecture Decision Table

| Concern | Final Monolithic Decision |
|---|---|
| Frontend | Vue SPA |
| Backend | One Spring Boot application |
| Architecture style | Modular monolith |
| Modules | Security, Product, Cart, Order, Checkout |
| Module communication | Typed in-process APIs |
| API Gateway | Removed |
| Internal REST | Removed |
| Service discovery | Removed |
| Database | One MongoDB deployment |
| Collections | `products`, `carts`, `orders` |
| Persistence ownership | Logical ownership by module |
| Checkout | Dedicated workflow |
| Checkout transaction | One MongoDB multi-document transaction |
| Transaction owner | Checkout |
| Cart/Order transaction participation | Same ambient Spring transaction |
| ClientSession exposure | Forbidden |
| Cart concurrency | `version` / revision |
| Stale checkout | `409 Conflict`, no automatic retry |
| Idempotency | Unique `(userId, idempotencyKey)` |
| Duplicate race | Losing transaction re-reads committed order |
| Product price | Product-owned current data |
| Checkout price | Server-owned Cart snapshot |
| Baseline checkout Product read | None |
| Baseline order state | `CONFIRMED` |
| Authentication | Keycloak |
| Protocol | OIDC / OAuth 2.0 |
| Browser flow | Authorization Code + PKCE |
| SPA type | Public client |
| API auth | JWT Bearer token |
| JWT validation | One Spring Security boundary |
| JWT audience | `shopping-cart-api` |
| Roles | USER / ADMIN / DEVELOPER |
| Identity | JWT `sub` |
| Role extraction | Custom converter for `realm_access.roles` |
| Token storage | In-memory access token |
| RBAC | Backend enforced |
| Ownership | Backend enforced |
| Admin order endpoint | `GET /api/admin/orders/{orderId}` |
| CORS | Spring Boot/Security |
| Error model | Problem Details |
| Logging | Structured application logging |
| Correlation | Request ID |
| Metrics | Actuator + Micrometer |
| Health | Liveness + readiness |
| MongoDB local/test | Single-node replica set |
| Testing | Unit + module + integration + architecture + security + E2E |
| Browser E2E | Playwright |
| CI | Compile → tests → build/package |
| Production deployment | One Spring Boot artifact/container |
| Horizontal scaling | Supported, coarse-grained |
| Distributed tracing | Deferred/out of scope |
| Kubernetes | Deferred/out of scope |
| Service mesh | Deferred/out of scope |

---

# 77. Master Architecture Trade-Offs

## Simplicity over distributed isolation

The system sacrifices independent service deployment and scaling in exchange for:

- fewer network failure modes;
- simpler operations;
- simpler local setup;
- atomic local checkout consistency;
- fewer security boundaries to configure.

## Logical boundaries over process boundaries

Product, Cart, Order, and Checkout remain separate conceptual modules even though they share the JVM.

This preserves the option to extract a module later if there is a real reason.

## Strong consistency over distributed compensation

The monolith uses a local MongoDB transaction rather than reproducing the microservice best-effort compensation model.

## Production-minded over production-scale

The architecture includes:

- health;
- metrics;
- logs;
- CI;
- security;
- backups;
- deployment;
- failure handling;

without requiring:

- Kubernetes;
- service mesh;
- distributed tracing;
- broker infrastructure;
- multi-region systems.

---

# 78. Future Evolution Path

The system can evolve without immediately returning to microservices.

Possible progression:

```text
Current Modular Monolith
        ↓
Stronger CI/CD
        ↓
Centralized metrics/logging
        ↓
Automated deployment
        ↓
Higher workload / independent team ownership
        ↓
Evaluate module extraction
        ↓
Introduce service contracts
        ↓
Introduce service-to-service security
        ↓
Introduce distributed tracing
```

A module should become a separate service only when there is a concrete reason such as:

- independent scaling pressure;
- independent release requirements;
- organizational ownership;
- fault-isolation requirements;
- operational necessity.

---

# 79. What Makes This a Modular Monolith

The architecture should not be described merely as:

> "Three services became one service."

The actual architecture is:

```text
One runtime
+
Multiple protected business modules
+
Owned persistence
+
Typed module contracts
+
Enforced dependency rules
+
One application security boundary
+
One local transaction boundary
```

That combination provides modularity without requiring network-level service boundaries.

---

# 80. Final Master Position

The Shopping Cart monolithic experiment is a **modular monolith with a single Spring Boot runtime and a single MongoDB deployment**, while retaining logical boundaries around Product, Cart, Order, Checkout, and Security.

It intentionally removes distributed infrastructure where it no longer provides value and reallocates that complexity budget to:

- stronger checkout consistency;
- concurrency control;
- idempotency;
- security;
- module-boundary enforcement;
- integration testing;
- observability;
- deployment simplicity.

The architecture is therefore not "microservices collapsed into one process."

It is a deliberately designed modular-monolith architecture.

---

# 81. Master Status

**MASTER ARCHITECTURE — READY**

This master architecture consolidates the independently reviewed and finalized:

- Group 1 — Core Modular Monolith Architecture;
- Group 2 — Security Architecture;
- Group 3 — Testing & Production Readiness Architecture.

The master introduces no new architectural decisions that conflict with those group documents.

Where concerns overlap, the consolidated position is:

```text
Group 1
Core modules + data + checkout consistency
        ↓
Group 2
Authentication + authorization + ownership
        ↓
Group 3
Testing + observability + deployment + operations
        ↓
MASTER
Complete monolithic architecture
```

The three group documents remain detailed supporting references; this document is the consolidated system-level source of truth for the monolithic experiment.
