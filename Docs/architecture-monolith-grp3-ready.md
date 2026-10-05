# Group 3 — Detailed Testing & Production Readiness Architecture
## Verification, Observability, Deployment, Reliability & Operational Architecture

**Project:** Shopping Cart Full-Stack Web Application  
**Stack:** Vue.js + Spring Boot 4.1.1 (Java 21) + MongoDB + Keycloak  
**Architecture:** Modular Monolith  
**Status:** CANDIDATE — PENDING INDEPENDENT CLAUDE REVIEW  
**Implementation constraint:** Approximately 2 hours for the academic implementation

---

# 1. Purpose

This document defines the architecture required to **test, verify, observe, operate, package, deploy, and evaluate** the modular monolith.

Group 1 defines the core modular-monolith architecture.

Group 2 defines authentication, authorization, RBAC, JWT validation, and ownership.

Group 3 therefore does not redefine those decisions.

Instead, Group 3 establishes the supporting architecture required to demonstrate that those decisions actually work in the running system.

The architecture covers:

- application testing;
- module-boundary verification;
- persistence and transaction verification;
- concurrency testing;
- security testing;
- API/error contracts;
- logging;
- correlation IDs;
- metrics;
- health/readiness;
- configuration;
- local development;
- Docker;
- CI/CD;
- deployment;
- scaling;
- failure behavior;
- backup/recovery considerations;
- production-readiness criteria.

The central principle is:

> **A modular monolith should be operationally simpler than the microservice architecture it replaces, while using that simplicity to strengthen correctness, module boundaries, and integration testing.**

---

# 2. Final Runtime Architecture

The production-minded baseline is one Spring Boot 4.1.1 application on Java 21.

```text
                           ┌──────────────────────┐
                           │       Keycloak       │
                           │    External OIDC     │
                           └──────────┬───────────┘
                                      │
                              OIDC / JWT
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────┐
│                         Vue SPA                             │
│                                                             │
│ Product browsing                                            │
│ Cart management                                             │
│ Checkout                                                    │
│ Order history                                               │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              │ HTTPS
                              │ Authorization: Bearer <JWT>
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    SPRING BOOT MONOLITH                     │
│                                                             │
│  Spring Security                                             │
│       │                                                      │
│       ▼                                                      │
│  Request / Correlation Filter                               │
│       │                                                      │
│       ▼                                                      │
│  ┌───────────┬───────────┬───────────┬───────────────┐    │
│  │  Product  │   Cart    │   Order   │   Checkout    │    │
│  │  Module   │  Module   │  Module   │    Module     │    │
│  └───────────┴───────────┴───────────┴───────────────┘    │
│                         │                                   │
│                         ▼                                   │
│              Application / Domain Services                 │
│                         │                                   │
│                         ▼                                   │
│                      MongoDB                                │
│                                                             │
│ Health / Metrics / Logging / Error Handling                │
└─────────────────────────────────────────────────────────────┘
```

There is no:

- Spring Cloud Gateway;
- Product runtime service;
- Cart runtime service;
- Order runtime service;
- service discovery;
- service-to-service HTTP;
- service-to-service authentication;
- distributed tracing requirement.

The runtime boundary is the Spring Boot application.

The logical module boundaries remain inside that application.

---

# 3. Operational Architecture

## 3.1 Application boundary

The Spring Boot process owns:

```text
Security
Product
Cart
Order
Checkout
API
Validation
Error handling
Logging
Metrics
Health
Configuration
```

All requests enter through one application boundary.

The application establishes the authenticated Spring Security context once.

Modules then consume the authenticated context and enforce their own resource and business rules.

---

# 4. Logical Module Boundary

The monolith must not become a single unrestricted package.

Recommended structure:

```text
com.example.shoppingcart
│
├── security/
│
├── product/
│   ├── api/
│   └── internal/
│
├── cart/
│   ├── api/
│   └── internal/
│
├── order/
│   ├── api/
│   └── internal/
│
├── checkout/
│   ├── api/
│   └── internal/
│
└── shared/
```

The exact Java package structure can vary, but the architectural rule remains:

```text
Public module API
        ↓
Allowed dependency
        ↓
Private module implementation
        ↓
Repository / persistence
```

A module's repository is not another module's public API.

---

# 5. Module Dependency Architecture

The dependency direction is intentionally constrained.

```text
                         ┌─────────────┐
                         │  Security   │
                         └──────┬──────┘
                                │
                 ┌──────────────┼──────────────┐
                 │              │              │
                 ▼              ▼              ▼
             Product          Cart           Order
                                │              │
                                └──────┬───────┘
                                       ▼
                                    Checkout
```

The exact implementation may allow Checkout to consume public APIs exposed by Cart and Order.

The critical rule is:

```text
Checkout → Cart API
Checkout → Order API

Checkout ✗ Cart repository
Checkout ✗ Order repository
```

Similarly:

```text
Cart ✗ Order repository
Order ✗ Cart repository
Product ✗ Cart repository
Product ✗ Order repository
```

This prevents the monolith from collapsing into direct database coupling.

---

# 6. Transaction Boundary

Checkout owns the transaction boundary.

```text
HTTP POST /api/orders/checkout
            │
            ▼
        Checkout
            │
      @Transactional
            │
      ┌─────┴─────┐
      │           │
      ▼           ▼
    Cart API    Order API
      │           │
      ▼           ▼
    Cart DB     Order DB
```

Cart and Order participate in the same Spring-managed MongoDB transaction.

The public module APIs must not accept or expose `ClientSession`.

The transaction mechanism remains infrastructure/application-layer behavior.

Conceptually:

```text
Checkout
   |
   | transaction starts
   |
   +--> load cart
   |
   +--> validate cart version
   |
   +--> create order
   |
   +--> finalize / clear cart
   |
   +--> commit
```

Failure before commit results in rollback.

---

# 7. MongoDB Architecture

One MongoDB deployment is used.

Logical ownership remains explicit:

```text
MongoDB
│
├── products
│       └── Product module
│
├── carts
│       └── Cart module
│
└── orders
        └── Order module
```

The shared database deployment does not imply shared ownership.

The application must preserve collection ownership at the module boundary.

---

# 8. MongoDB Transaction Requirement

The checkout architecture requires MongoDB transaction support.

Therefore the MongoDB deployment used for development, testing, and production-like execution must support transactions.

A transaction-capable replica-set configuration is required. A **single-node replica set is sufficient** for this project's development and test environments; a multi-node cluster is not required for the two-hour implementation.

Conceptually:

```text
Spring Boot
     │
     │ Mongo transaction
     ▼
MongoDB replica set
```

A simple standalone MongoDB deployment must not be presented as equivalent to the production checkout environment if it cannot support the required transaction model.

---

# 9. Persistence Responsibilities

## Product module

Owns:

```text
products
```

Responsible for:

- product persistence;
- product validation;
- product price;
- product administration.

## Cart module

Owns:

```text
carts
```

Responsible for:

- cart persistence;
- cart ownership;
- cart version;
- cart item snapshots;
- cart state transitions.

## Order module

Owns:

```text
orders
```

Responsible for:

- order persistence;
- immutable order snapshots;
- order ownership;
- idempotency records;
- order status.

## Checkout module

Does not own a new collection.

It orchestrates the checkout transaction through Cart and Order public APIs.

---

# 10. Critical Database Indexes

At minimum:

```text
carts
    unique(userId)

orders
    unique(userId, idempotencyKey)

products
    unique(id)
```

Additional indexes should match actual query patterns.

Examples:

```text
orders.userId
orders.createdAt
```

Index creation must be reproducible across environments.

---

# 11. Testing Architecture

The testing strategy follows five layers:

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
                    + Persistence/Concurrency Tests
```

The monolith places greater emphasis on **application integration testing** than the original distributed architecture because there are fewer runtime boundaries to test.

---

# 12. Unit Testing

Unit tests validate isolated business logic.

Primary candidates:

```text
price calculations
order total calculation
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

Unit tests should be fast and deterministic.

They must not attempt to simulate MongoDB transaction semantics.

---

# 13. Module Testing

Each module is tested through its public behavior.

## Product

```text
valid creation
invalid creation
read existing product
read missing product
admin mutation
non-admin mutation rejection
```

## Cart

```text
create/load cart
add item
increase quantity
decrease quantity
remove item
clear cart
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

# 14. Architecture Testing

Architecture tests are a first-class part of the modular-monolith design.

They should verify:

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

The objective is architectural enforcement, not tool accumulation.

---

# 15. Persistence Integration Testing

Persistence tests use a real MongoDB-compatible environment.

They verify behavior that mocks cannot reliably prove.

Mandatory candidates:

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

A mocked repository is insufficient for testing database uniqueness and transaction semantics.

---

# 16. Transaction Integration Tests

The transaction test architecture is:

```text
Arrange
  ↓
Populate cart
  ↓
Begin checkout
  ↓
Perform order + cart operations
  ↓
Commit
  ↓
Verify both collections
```

For rollback:

```text
Arrange
  ↓
Populate cart
  ↓
Inject controlled failure
  ↓
Abort transaction
  ↓
Verify order absent
  ↓
Verify cart unchanged
```

The test must validate actual transaction semantics rather than merely checking that an exception was thrown.

---

# 17. Checkout Concurrency Architecture

Checkout has two important concurrency dimensions.

## 17.1 Cart version conflict

Cart has:

```text
version
```

Flow:

```text
Request A
    loads version 7
            │
            │
Request B modifies cart
            │
            ▼
       version 8
            │
            ▼
Request A commits checkout
            │
            ▼
       version mismatch
            │
            ▼
       transaction abort
            │
            ▼
       409 Conflict
```

The application does not silently merge the changed cart.

It also does not automatically retry checkout.

The client must reload the cart and explicitly retry.

## 17.2 Same idempotency key

Two simultaneous requests:

```text
                    User + Key K
                         │
              ┌──────────┴──────────┐
              │                     │
         Request A             Request B
              │                     │
              └──────────┬──────────┘
                         ▼
                 Unique DB index
                         │
                 ┌───────┴────────┐
                 │                │
              Winner            Loser
                 │                │
                 ▼                ▼
           Commit order      Duplicate-key
                              handling
                                   │
                                   ▼
                           Re-read order
                                   │
                                   ▼
                              Return order
```

The loser must not return an unexplained `500`.

This is a database-level correctness property.

---

# 18. API Verification Architecture

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

The API contract can be represented through OpenAPI.

There is no need for microservice-specific contract testing between modules because there are no independent runtime service APIs.

---

# 19. Standard Error Architecture

All backend errors pass through a common exception-handling layer.

```text
Module exception
       │
       ▼
Global exception handler
       │
       ▼
ProblemDetail
       │
       ▼
HTTP response
```

Targeted mappings include:

```text
400 → validation/request error
401 → authentication failure
403 → authorization failure
404 → protected resource absent/not disclosed
409 → business/state conflict
500 → unexpected server failure
```

The browser must never receive stack traces or database internals.

---

# 20. Correlation-ID Architecture

The application generates or accepts a request/correlation ID.

```text
HTTP request
     │
     ▼
Correlation filter
     │
     ├──────► Security
     │
     ├──────► Product
     │
     ├──────► Cart
     │
     ├──────► Order
     │
     └──────► Checkout
                 │
                 ▼
               Logs
```

Because the entire flow is in one process, correlation IDs are used primarily for request-to-log correlation.

Distributed trace reconstruction is unnecessary.

Response:

```text
X-Request-Id: <request-id>
```

may be returned to the client.

---

# 21. Logging Architecture

Use application-level structured logging.

Recommended fields:

```text
timestamp
level
requestId
HTTP method
path
status
duration
authenticated subject where appropriate
module
operation
exception type
```

Sensitive information must never be logged:

```text
access tokens
refresh tokens
passwords
client secrets
Authorization headers
unnecessary personal information
```

Security and checkout failures should be distinguishable from generic infrastructure failures.

---

# 22. Metrics Architecture

Spring Boot Actuator + Micrometer provide the baseline metrics layer.

Important measurements include:

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

The application exposes metrics.

A full Prometheus/Grafana deployment is optional and not required for the baseline.

---

# 23. Health Architecture

The monolith exposes:

```text
/actuator/health
```

The production-oriented model distinguishes:

```text
Liveness
Readiness
```

### Liveness

Indicates that the application process is alive.

### Readiness

Indicates that the application can serve requests and required dependencies are available.

Health endpoints must not expose:

- environment variables;
- secrets;
- database credentials;
- internal configuration;
- unrestricted diagnostic information.

---

# 24. Configuration Architecture

Environment-specific configuration is externalized.

Example variables:

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

The application code remains the same across environments wherever possible.

The configuration changes.

---

# 25. Security Configuration Integration

Group 2 defines the security model.

Group 3 verifies it operationally.

The production configuration must guarantee:

```text
dev authentication shortcuts → disabled
real JWT validation          → enabled
Keycloak issuer              → configured
audience validation          → enabled
CORS                         → explicit
Actuator exposure            → restricted
```

Security tests must be part of CI.

---

# 26. Development Environment

Recommended local topology:

```text
┌─────────────────────────────────┐
│ Developer Machine               │
│                                 │
│ Vue Dev Server                  │
│ Spring Boot Monolith            │
│                                 │
│        │                        │
│        ▼                        │
│ Docker Compose                 │
│ ├── MongoDB                    │
│ └── Keycloak                   │
└─────────────────────────────────┘
```

The Spring Boot application may run directly from the IDE during development.

MongoDB and Keycloak can be containerized to standardize infrastructure dependencies. The MongoDB container must be configured as a **single-node replica set** (for example, started with `--replSet rs0` and initialized with `rs.initiate()` or an equivalent Compose startup mechanism) so the local environment supports the same transaction semantics used by checkout.

---

# 27. Test Environment

The test environment should isolate:

```text
Application
MongoDB
Keycloak/test identity configuration
Test data
```

Test data must be generated/reset by tests.

Tests must not depend on a developer's existing database state. The MongoDB integration-test environment must use the same **single-node replica-set configuration** as development so transaction tests exercise the required checkout semantics rather than a standalone MongoDB configuration.

---

# 28. Production Deployment Architecture

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

Keycloak remains the external identity provider.

MongoDB is externally managed or deployed using a production-capable configuration.

---

# 29. Horizontal Scaling

The monolith can scale horizontally.

```text
                 Load Balancer
                 /     |     \
                /      |      \
               ▼       ▼       ▼
             App 1   App 2   App 3
                \      |      /
                 \     |     /
                  ▼    ▼    ▼
                    MongoDB
```

The application must therefore remain stateless with respect to business state.

Persistent state lives in MongoDB.

Browser tokens remain client-side according to Group 2.

---

# 30. Scaling Trade-Off

Horizontal scaling is possible, but scaling remains coarse-grained.

For example:

```text
High checkout traffic
        ↓
Scale entire application
        ↓
Product + Cart + Order also scale
```

This is accepted for the current system.

Independent module scaling is intentionally not provided because the modules are not separate runtime services.

---

# 31. Caching Architecture

No mandatory application cache is introduced.

Future read caching may use something such as Caffeine for relatively static product reads.

However:

```text
Cache ≠ source of truth
```

Checkout correctness must remain based on authoritative persisted data.

Caching must not allow the application to calculate an order using an untrusted or stale price snapshot outside the Group 1 rules.

---

# 32. Retry Architecture

Internal module calls do not require retries because they are in-process.

Potential retries remain relevant for infrastructure/network boundaries:

```text
MongoDB
Keycloak
reverse proxy
external future integrations
```

Do not automatically retry non-idempotent business operations.

Checkout retries use the same `Idempotency-Key`.

---

# 33. Timeout Architecture

No inter-module HTTP timeouts exist.

Applicable timeout boundaries are:

```text
Browser → Spring Boot
Spring Boot → MongoDB
Spring Boot → Keycloak/external systems
Reverse Proxy → Spring Boot
```

Timeout values are environment/deployment configuration.

---

# 34. Failure Handling

## MongoDB unavailable

```text
Database operation fails
        ↓
Global exception handling
        ↓
Safe error response
        +
Error log/metric
        +
Readiness reflects dependency problem
```

The application does not fabricate successful orders.

## Keycloak unavailable

Existing valid JWT processing may continue while required signing metadata remains available according to the JWT validation configuration.

New authentication/metadata acquisition may fail.

The application must not fall back to an insecure local authentication mode in production.

## Application crash

MongoDB-persisted business state survives process termination.

Clients may retry operations according to API semantics.

Checkout remains protected by idempotency.

---

# 35. Graceful Shutdown

The deployment must support graceful termination.

```text
SIGTERM
   │
   ▼
Stop receiving new requests
   │
   ▼
Complete in-flight requests
   │
   ▼
Close resources
   │
   ▼
Terminate process
```

This is important because one process contains all logical modules.

A deployment restart therefore affects Product, Cart, Order, and Checkout simultaneously.

---

# 36. Backup and Recovery Architecture

Production MongoDB must have a backup strategy.

The application itself does not implement backups.

The deployment environment should provide:

```text
Scheduled backups
Retention policy
Restore procedure
```

The database is the authoritative persistent state for:

```text
products
carts
orders
```

Recovery testing should eventually demonstrate that orders and carts can be restored without corrupting idempotency state.

For the academic implementation, actual disaster-recovery automation is documented rather than implemented.

---

# 37. CI Architecture

Recommended pipeline (see §46 for the subset required within the two-hour implementation):

```text
Commit
  │
  ▼
Compile
  │
  ▼
Unit tests
  │
  ▼
Module tests
  │
  ▼
Architecture tests
  │
  ▼
Security tests
  │
  ▼
Mongo integration tests
  │
  ▼
Concurrency/transaction tests
  │
  ▼
Frontend build
  │
  ▼
Playwright E2E
  │
  ▼
Build/package
```

Critical failures stop the pipeline.

---

# 38. CI Test Tiers

## Pull Request

Run fast validation:

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

# 39. Docker Architecture

### Local development

Docker Compose may run:

```text
MongoDB
Keycloak
```

Spring Boot and Vue may run locally.

### Integration environment

A complete containerized topology may be used:

```text
Vue
Spring Boot
MongoDB
Keycloak
```

### Production

The Spring Boot application is packaged as one deployable artifact/container.

The monolith does not require three backend containers.

---

# 40. Container Health

The application container should expose a health mechanism that can be connected to the deployment platform.

Conceptually:

```text
Container
    │
    ├── liveness
    └── readiness
```

Deployment orchestration should remove unhealthy/unready instances from traffic rather than routing requests to them.

---

# 41. API Smoke Architecture

After deployment:

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

The purpose is to verify the complete external request path.

---

# 42. End-to-End Architecture

The primary Playwright scenario:

```text
Browser
  │
  ▼
Vue
  │
  ▼
Keycloak authentication
  │
  ▼
Spring Boot
  │
  ├── Security
  ├── Product
  ├── Cart
  ├── Checkout
  └── Order
  │
  ▼
MongoDB
```

The golden path:

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

This single flow verifies a large portion of the system integration.

---

# 43. Critical Security Verification

Group 3 must verify Group 2's security architecture.

Required tests include:

```text
missing JWT                         → 401
expired JWT                         → 401
wrong issuer                       → 401
wrong audience                     → 401
invalid signature                  → 401

USER → admin product mutation      → 403
USER → admin order lookup          → 403
ADMIN → admin order lookup        → success
User A → User B cart               → rejected
User A → User B order              → 404
```

The tests must use the authenticated JWT subject rather than client-supplied identity fields.

---

# 44. Critical Checkout Verification

Required tests:

```text
normal checkout                    → success
empty cart                         → 409
stale cart version                 → 409
same idempotency key               → same order
concurrent same key                → one logical order
different users + same key         → independent orders
transaction failure                → rollback
client-modified price              → ignored/rejected
```

These are higher-value than exhaustive CRUD test coverage.

---

# 45. Definition of Done

The architecture is operationally acceptable when:

```text
Application starts
        ↓
MongoDB transaction capability verified
        ↓
Keycloak reachable
        ↓
Authentication works
        ↓
JWT validation works
        ↓
Product operations work
        ↓
Cart ownership works
        ↓
Checkout transaction works
        ↓
Order persisted
        ↓
Idempotency works
        ↓
Conflict behavior works
        ↓
Security tests pass
        ↓
Architecture tests pass
        ↓
Golden-path E2E passes
        ↓
Health endpoint is healthy
```

---

# 46. 2-Hour Implementation Priorities

## Must implement

1. JUnit 5 test foundation.
2. Critical Product tests.
3. Critical Cart tests.
4. Critical Order/Checkout tests.
5. Spring Security tests.
6. MongoDB persistence tests.
7. Idempotency uniqueness.
8. Checkout transaction test.
9. Cart-version conflict test.
10. Problem Details.
11. Structured logging.
12. Request/correlation ID.
13. `/actuator/health`.
14. Environment configuration.
15. Docker Compose infrastructure.
16. One Playwright golden path.

## Implement if time permits

- ArchUnit/Spring Modulith verification.
- Additional concurrency tests.
- Metrics verification.
- Containerized Spring Boot image.
- Dependency vulnerability scanning.
- Additional negative Playwright cases.

## Document only

- Kubernetes.
- Prometheus/Grafana.
- Centralized log platform.
- Distributed tracing.
- Service mesh.
- Multi-region deployment.
- Advanced disaster recovery.
- Blue/green or canary deployment.

---

# 47. Risks

## Single-process blast radius

A runtime failure affects every module.

Mitigation:

- strong automated verification;
- health/readiness;
- gradual deployment practices;
- rollback capability.

## Module boundary erosion

Developers can bypass APIs because all code runs in the same application.

Mitigation:

- architecture tests;
- package visibility;
- code review;
- explicit module ownership.

## Coarse-grained scaling

All modules scale together.

Accepted because the current application does not justify independent runtime scaling.

## Shared database temptation

Developers may bypass module ownership.

Mitigation:

- collection ownership;
- module APIs;
- architecture tests.

## Transaction environment mismatch

Development MongoDB may differ from production assumptions.

Mitigation:

- standard transaction-capable test environment;
- explicit MongoDB configuration;
- integration tests exercising actual transactions.

---

# 48. Future Evolution

The operational architecture can evolve without immediately splitting the application.

Recommended progression:

```text
Current modular monolith
        ↓
Better CI/CD
        ↓
Central metrics/logging
        ↓
Automated deployment
        ↓
Increased workload / team ownership
        ↓
Evaluate independent scaling
        ↓
Extract only justified module
        ↓
Introduce service contracts
        ↓
Introduce service-to-service security
        ↓
Introduce distributed tracing
```

A module should become a service only when there is a concrete reason for it.

---

# 49. What Is Explicitly Removed Compared With the Microservice Production Model

The following are no longer baseline requirements:

```text
Service discovery
Service registry
API Gateway
Inter-service load balancing
Inter-service HTTP timeouts
Inter-service retry policies
Circuit breakers between business modules
Distributed tracing
Service-to-service authentication
JWT forwarding
Contract tests between Cart and Order services
Inter-service network monitoring
Saga/compensation infrastructure caused by service failure
```

These are removed because the corresponding runtime/network boundaries no longer exist.

---

# 50. What Remains Critical

The monolith still requires:

```text
Authentication
Authorization
IDOR protection
Validation
Idempotency
Transactions
Concurrency control
Persistence integrity
API error contracts
Logging
Correlation IDs
Health checks
Metrics
Configuration separation
Automated testing
Deployment reproducibility
Backups
```

Simplifying deployment topology does not remove application correctness requirements.

---

# 51. Final Architecture

```text
                         ┌───────────────────┐
                         │      Keycloak     │
                         │      OIDC/JWT     │
                         └─────────┬─────────┘
                                   │
                                   ▼
                         ┌───────────────────┐
                         │      Vue SPA      │
                         └─────────┬─────────┘
                                   │ HTTPS
                                   ▼
┌───────────────────────────────────────────────────────────┐
│                 SPRING BOOT MONOLITH                     │
│                                                           │
│  ┌─────────────── Application Boundary ────────────────┐ │
│  │                                                     │ │
│  │ Security                                             │ │
│  │ Request/Correlation IDs                             │ │
│  │ Problem Details                                      │ │
│  │                                                     │ │
│  │ Product       Cart        Order        Checkout      │ │
│  │   │            │            │             │         │ │
│  │   └────────────┴────────────┴─────────────┘         │ │
│  │                        │                             │ │
│  │                  Transaction                        │ │
│  │                        │                             │ │
│  └────────────────────────┼─────────────────────────────┘ │
│                           │                               │
│              Logs / Metrics / Health                      │
└───────────────────────────┼───────────────────────────────┘
                            │
                            ▼
                    ┌─────────────────┐
                    │ MongoDB         │
                    │                 │
                    │ products        │
                    │ carts           │
                    │ orders          │
                    └─────────────────┘

Verification:
───────────────────────────────────────────────────────────
Unit tests
Module tests
Architecture tests
Security tests
Persistence tests
Transaction/concurrency tests
API tests
Playwright E2E
CI pipeline
Health/metrics/logging
```

---

# 52. Final Architectural Principles

1. **One runtime does not mean one logical module.**

2. **Testing must protect business invariants, not merely code coverage.**

3. **Checkout transaction semantics must be tested against real MongoDB behavior.**

4. **Concurrency and idempotency deserve explicit tests because they protect correctness under retries and simultaneous requests.**

5. **Architecture tests prevent the modular monolith from becoming an uncontrolled monolith.**

6. **Security tests verify Group 2 rather than creating a second security model.**

7. **Observability remains valuable, but distributed tracing is unnecessary without distributed runtime boundaries.**

8. **The production deployment should be simpler than the microservice deployment without being careless.**

9. **Operational state belongs in persistent infrastructure, not process-local memory.**

10. **Infrastructure should be introduced only when the application has a reason to need it.**

---

# 53. Status

**GROUP 3 — DETAILED TESTING & PRODUCTION READINESS ARCHITECTURE: CANDIDATE FOR INDEPENDENT CLAUDE REVIEW**

This document is intentionally separate from:

- Group 1 — Core Modular Monolith Architecture
- Group 2 — Security Architecture

It defines the architecture required to **verify, operate, deploy, observe, and evolve** the system described by Groups 1 and 2.

It removes microservice-only operational mechanisms rather than reproducing them inside the monolith, while adding monolith-specific verification for:

- module boundaries;
- MongoDB transactions;
- checkout concurrency;
- idempotency races;
- application-wide observability;
- deployment simplicity;
- coarse-grained scaling;
- single-process failure behavior.
