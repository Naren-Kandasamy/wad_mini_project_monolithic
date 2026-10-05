# Group 2 — Security Architecture
## OAuth/OIDC, RBAC, JWT, Resource Ownership & Security for the Modular Monolith

**Project:** Shopping Cart Full-Stack Web Application  
**Stack:** Vue.js + Spring Boot + MongoDB + Keycloak  
**Architecture:** Modular Monolith  
**Status:** RESEARCH-INFORMED — PENDING INDEPENDENT CLAUDE REVIEW  
**Implementation constraint:** Approximately 2 hours

---

# 1. Purpose

This document defines the security architecture for the shopping-cart application after consolidation into a **modular monolith**.

The backend is one Spring Boot application containing the Product, Cart, Order, Checkout, and Security concerns as logical modules. Security therefore has one application-level authentication boundary, while resource ownership and business authorization remain enforced at the relevant module and use-case boundaries.

The architecture preserves application-security controls that are independent of deployment topology while removing security mechanisms whose sole purpose was protecting service-to-service network calls.

The design covers:

- authentication;
- OpenID Connect and OAuth 2.0;
- JWT access tokens;
- Keycloak;
- RBAC;
- resource ownership and IDOR prevention;
- module-level authorization;
- frontend token handling;
- CORS and edge security;
- secrets and configuration;
- security testing;
- security logging and operational considerations.

The architecture does **not** introduce a second authentication system merely because the backend is monolithic.

---

# 2. Core Security Decisions

| Decision | Monolithic Architecture Decision |
|---|---|
| Identity Provider | **Keycloak** |
| Authentication protocol | **OpenID Connect (OIDC)** on OAuth 2.0 |
| Browser flow | **Authorization Code + PKCE** |
| SPA client type | **Public client** |
| API authentication | Bearer access token / JWT |
| JWT issuer | Keycloak |
| JWT validation | **One application-level Spring Security boundary** |
| JWT signature validation | Required |
| JWT issuer validation | Required |
| JWT expiration validation | Required |
| JWT audience validation | Required |
| User identity | JWT `sub` claim |
| Roles | `USER`, `ADMIN`, `DEVELOPER` |
| Role source of truth | Keycloak JWT role claims |
| Authorization | Application route-level checks + module/use-case authorization |
| Resource ownership | Enforced by Product/Cart/Order/Checkout module logic as applicable |
| User-specific APIs | `/me` pattern where applicable |
| Access-token storage | In-memory in Vue application state |
| `localStorage` access tokens | **Not used** |
| Custom authentication server | Not built |
| Custom `/refresh` endpoint | Not built |
| Service-to-service authentication | **Not applicable inside the monolith** |
| JWT forwarding between modules | **Not applicable** |
| Internal REST authentication | **Not applicable** |
| CSRF | Not required for pure bearer-token Authorization-header API model; revisit if cookie auth is introduced |
| CORS | Spring Boot/Spring Security application boundary |
| Rate limiting | Optional application/deployment edge enhancement |
| Token revocation | Future work |
| Production secrets manager | Future work |

The central architectural distinction is:

> **The monolith has one authentication boundary, but it does not have one undifferentiated authorization boundary.**

Modules remain responsible for protecting the resources they own.

---

# 3. Security Architecture Shape

```text
                         ┌──────────────────────┐
                         │       Keycloak       │
                         │                      │
                         │ OIDC / OAuth 2.0     │
                         │ Users / Roles        │
                         │ JWT issuance         │
                         └──────────┬───────────┘
                                    │
                         Authorization Code + PKCE
                                    │
                                    ▼
┌────────────────┐       ┌────────────────────────────┐
│                │       │        Vue SPA             │
│    Browser     │◀─────▶│ public OIDC client         │
│                │       │ access token in memory     │
└────────────────┘       └──────────────┬─────────────┘
                                       │
                                       │ Authorization:
                                       │ Bearer <JWT>
                                       ▼
                         ┌────────────────────────────┐
                         │       Spring Boot          │
                         │      MODULAR MONOLITH      │
                         │                            │
                         │ Spring Security boundary   │
                         │       ↓                    │
                         │ Authenticated principal    │
                         │       ↓                    │
                         │ ┌──────────┬───────────┐   │
                         │ │ Product  │ Cart      │   │
                         │ │ module   │ module    │   │
                         │ ├──────────┼───────────┤   │
                         │ │ Order    │ Checkout  │   │
                         │ │ module   │ module    │   │
                         │ └──────────┴───────────┘   │
                         └──────────────┬─────────────┘
                                        │
                                        ▼
                                   MongoDB
```

There is **no Spring Cloud Gateway** in this security architecture.

There are no backend service-to-service JWT hops, no internal HTTP authentication, and no `/internal/**` network endpoints. Cross-module calls occur through the typed in-process interfaces defined by the core architecture.

---

# 4. Threat Model

The primary security threats are application-level rather than service-to-service network threats.

| Threat | Primary Mitigation |
|---|---|
| Forged/invalid token | JWT signature, issuer, expiration and audience validation |
| Missing authentication | Spring Security authentication boundary |
| Privilege escalation | RBAC + backend authorization |
| IDOR / horizontal privilege escalation | JWT-sub-derived ownership checks |
| Forged identity fields | Ignore client-supplied identity; derive identity from validated principal |
| Client-modified price | Server-authoritative product/cart data |
| Client-modified order total | Server-side order total calculation |
| Duplicate checkout | `(userId, idempotencyKey)` uniqueness + checkout semantics |
| Mass assignment | DTOs / explicit server-side mapping |
| XSS token theft | In-memory token storage + frontend-safe rendering practices |
| CORS abuse | Explicit allowed origins |
| API abuse | Optional rate limiting / deployment edge controls |
| Exposed diagnostics | Restricted Actuator/security configuration |
| Credential leakage | Environment/local secret configuration; never commit secrets |
| Module boundary erosion | Explicit module APIs + architecture tests |
| Shared-process blast radius | Least privilege, module isolation, secure coding, monitoring |

The monolith removes some network-attack surfaces but **does not eliminate application security boundaries**.

---

# 5. Identity Provider — Keycloak

## 5.1 Why Keycloak remains

The move to a monolith does not by itself invalidate Keycloak.

Keycloak remains the external identity provider because it provides:

- OIDC authentication;
- OAuth 2.0 support;
- user management;
- role management;
- JWT issuance;
- JWKS/public-key publication;
- a standard external identity boundary;
- a path to shared identity if additional applications are introduced later.

A monolith could theoretically use application-managed sessions instead, but that is **not** adopted for this architecture merely because the backend is now monolithic.

The current experiment retains the existing Keycloak identity model so that the architecture comparison isolates the deployment/runtime change rather than simultaneously changing authentication technology.

## 5.2 Realm

```text
shopping-cart
```

## 5.3 SPA client

The Vue application is modeled as a **public client**.

A browser-delivered SPA must not contain a confidential client secret.

---

# 6. OAuth 2.0 vs OpenID Connect

OAuth 2.0 provides authorization capabilities; OpenID Connect adds the authentication/identity layer required to establish who the user is.

```text
OIDC
└── OAuth 2.0
    ├── Authorization
    └── Authentication / Identity
```

The application requires OIDC because authenticated user identity is used for:

- cart ownership;
- order ownership;
- authorization decisions;
- role-based access control;
- audit/security context;
- checkout identity.

---

# 7. Browser Authentication Flow

The Vue SPA uses **Authorization Code + PKCE**.

```text
Vue SPA
   │
   │ 1. Generate PKCE code_verifier
   │    and S256 code_challenge
   ▼
Keycloak
   │
   │ 2. User authenticates
   │
   │ 3. Authorization code
   ▼
Vue SPA
   │
   │ 4. Authorization code + verifier
   ▼
Keycloak
   │
   │ 5. Access token + ID token
   │    (+ refresh token if issued)
   ▼
Vue SPA
   │
   │ 6. Authorization: Bearer <access_token>
   ▼
Spring Boot
```

The deprecated implicit flow is not used.

The browser receives an access token for API access. The API must **not** use the ID token as its authorization credential.

---

# 8. Token Responsibilities

| Token | Purpose | Application Usage |
|---|---|---|
| Access token | API authorization | Sent in `Authorization: Bearer <token>` |
| ID token | Client-side identity information | Used by Vue for authenticated-user information only |
| Refresh token | Obtain new access token if provider/client flow issues one | Managed by the chosen OIDC client/provider flow |

The application must not implement its own refresh-token endpoint merely to demonstrate token renewal.

---

# 9. Frontend Token Storage

## 9.1 Access tokens

Access tokens remain in **memory** in the Vue application's authentication state/composable.

Do not store API access tokens in:

```text
localStorage
```

This reduces persistence of bearer credentials in browser storage and limits their lifetime to the active application state.

A browser refresh may require the OIDC client/provider session to establish authentication again.

## 9.2 Refresh tokens

No custom `/refresh` endpoint is implemented.

If refresh tokens are used, the selected OIDC client/provider flow manages them according to its documented browser security model. Before adopting that flow, the implementation must explicitly inspect the library/provider defaults for refresh-token storage. Persistent browser storage such as `localStorage` or other long-lived client-side storage must not be enabled silently. If the selected library requires persistent storage, that behavior and its XSS/token-theft trade-off must be documented and deliberately accepted as a separate security decision.

---

# 10. JWT Structure and Claims

Keycloak issues signed JWT access tokens.

The application primarily relies on:

- `sub` — canonical authenticated user identifier;
- `iss` — canonical Keycloak issuer;
- `exp` — expiration timestamp;
- `aud` — intended API audience;
- role claims such as `realm_access.roles`.

Conceptually:

```json
{
  "sub": "user-123",
  "iss": "https://keycloak/realms/shopping-cart",
  "aud": "shopping-cart-api",
  "exp": 1695123456,
  "iat": 1695120000,
  "realm_access": {
    "roles": ["USER"]
  },
  "preferred_username": "user"
}
```

The exact claims emitted by Keycloak are configuration-dependent. The architecture must not assume claims exist without configuring and verifying them.

---

# 11. Single Application JWT Validation

The monolithic backend validates JWTs **once at the application security boundary** using Spring Security.

```text
HTTP Request
    │
    ▼
Spring Security Filter Chain
    │
    ├── signature validation
    ├── issuer validation
    ├── expiration validation
    ├── audience validation
    └── role extraction
    │
    ▼
Authenticated principal
    │
    ├── Product authorization
    ├── Cart ownership
    ├── Order ownership
    └── Checkout authorization
```

There is no second JWT validation layer for Product, Cart, or Order because they are not separate resource-server processes.

## 11.1 Required validation

The application must validate:

1. JWT signature using Keycloak's public signing keys/JWKS.
2. Token issuer.
3. Token expiration/timestamps.
4. Expected audience.

An otherwise well-formed token with the wrong issuer or audience must be rejected.

## 11.2 Audience

The intended API audience is:

```text
shopping-cart-api
```

The application must explicitly configure audience validation rather than assuming JWT parsing automatically enforces the expected audience.

Keycloak must also be configured to emit `shopping-cart-api` as an audience claim (for example through an explicit Audience Mapper/client scope). Token issuance must be verified in the deployed realm rather than assuming the audience is present by default.

---

# 12. Spring Security Boundary

Conceptually:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) {
    // JWT resource-server validation
    // route-level authorization
    // CORS / security headers / request filtering
    return ...;
}
```

The application establishes one `SecurityContext` per authenticated request.

All Product, Cart, Order, and Checkout module code consumes the authenticated identity from Spring Security rather than receiving an arbitrary client-supplied `userId` or role.

---

# 13. Keycloak Role Model

The application uses three roles:

```text
USER
ADMIN
DEVELOPER
```

### USER

Regular customer capabilities:

- browse products;
- manage own cart;
- checkout;
- view own orders.

### ADMIN

Administrative capabilities:

- manage products;
- access explicitly defined administrative order resources where required.

Administrative access does **not** automatically imply permission to access every user resource unless the endpoint explicitly grants it.

### DEVELOPER

Testing/development-oriented role for the academic application.

It may access explicitly authorized test/diagnostic features where implemented.

`DEVELOPER` does not automatically imply `ADMIN`.

### STORE_OWNER

Not included. A seller/marketplace model would require vendor ownership and isolation rules beyond the current scope.

---

# 14. Keycloak Role Mapping

Keycloak roles are converted into Spring Security authorities.

Conceptually:

```text
Keycloak realm role
       ↓
JWT realm_access.roles
       ↓
JwtAuthenticationConverter
       ↓
ROLE_USER / ROLE_ADMIN / ROLE_DEVELOPER
       ↓
Spring Security authorization
```

The mapping must be explicitly configured for Keycloak's role claim structure.

The implementation must use a custom `Converter<Jwt, Collection<GrantedAuthority>>` (or equivalent Spring Security converter) that explicitly extracts the nested `realm_access.roles` collection and maps each role to the corresponding `ROLE_*` authority.

Conceptually:

```text
Jwt
  └── realm_access
        └── roles[]
              ↓
Custom Jwt → GrantedAuthority converter
              ↓
ROLE_USER / ROLE_ADMIN / ROLE_DEVELOPER
```

A dotted claim-name configuration alone must not be assumed to extract the nested Keycloak structure correctly. The converter implementation must match the actual JWT emitted by the configured Keycloak realm.

---

# 15. Authorization Model

Authorization is layered.

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

### Route-level authorization

Spring Security can enforce coarse requirements such as:

```text
/api/admin/**  → ROLE_ADMIN
/api/dev/**    → ROLE_DEVELOPER
/api/**        → authenticated users as required
```

### Module/use-case authorization

Fine-grained checks remain close to the operation they protect, potentially through `@PreAuthorize` and service/application-layer authorization.

For example:

```java
@PreAuthorize("hasRole('ADMIN')")
public Product createProduct(...) { ... }
```

The exact division between route checks and method checks must avoid relying on only one layer for sensitive business authorization.

---

# 16. Resource Ownership and IDOR Prevention

Consolidating Product, Cart, Order, and Checkout into one JVM does not eliminate IDOR.

Ownership must still be derived from the validated authenticated subject.

```text
JWT.sub
  ↓
Spring Security Authentication
  ↓
authenticatedUserId
  ↓
Cart / Order ownership queries
```

The backend must never treat these as authoritative identity sources:

```text
request body userId      ❌
query parameter userId   ❌
X-User-Id header         ❌
X-User-Role header       ❌
frontend route state     ❌
```

## `/me` pattern

User-owned APIs use `/me` where appropriate:

```http
GET    /api/carts/me
POST   /api/carts/me/items
PUT    /api/carts/me/items/{productId}
DELETE /api/carts/me/items/{productId}
DELETE /api/carts/me
```

Orders:

```http
GET  /api/orders
GET  /api/orders/{id}
POST /api/orders/checkout
GET  /api/admin/orders/{orderId}
```

`GET /api/orders` for a normal user returns only that user's orders.

`GET /api/orders/{id}` must explicitly verify ownership. For non-admin users, an order belonging to another user should be indistinguishable from a nonexistent order and return `404 Not Found`.

`GET /api/admin/orders/{orderId}` is the explicit administrative individual-order lookup endpoint and requires `ROLE_ADMIN`. This endpoint is covered by a positive authorization test as well as an unauthorized-role test.

---

# 17. Module Security Boundaries

The modular monolith has **logical security boundaries even though modules share one process**.

The core architecture defines public module APIs and private internals.

Security consequences:

- Cart must not bypass its ownership checks merely because Order is in the same JVM.
- Order must not bypass Cart's public API to manipulate another user's cart.
- Product must not expose repository internals as an authorization shortcut.
- Controllers must not directly reach another module's repositories.

Recommended package shape:

```text
product/
├── api/
└── internal/

cart/
├── api/
└── internal/

order/
├── api/
└── internal/

checkout/
```

Architecture tests should reject cross-module access to internal packages and repositories.

Spring Modulith or ArchUnit may be used to automate these checks; this is an architectural enforcement option, not a requirement to introduce additional runtime infrastructure.

---

# 18. No Service-to-Service Security Layer

The monolithic runtime has no internal REST service calls between Product, Cart, Order, and Checkout.

Therefore the following are removed:

```text
/internal/carts/{userId}          ❌
JWT forwarding between modules    ❌
Service-to-service JWT validation  ❌
Service-to-service API keys       ❌
mTLS between modules              ❌
Client-credentials service calls  ❌
```

Cross-module operations use typed in-process APIs and the existing authenticated Spring Security context.

This does **not** mean internal operations are implicitly authorized. The relevant module/use-case must still enforce role and ownership rules.

---

# 19. Gateway Responsibilities After Gateway Removal

There is no Spring Cloud Gateway in the baseline.

Responsibilities that previously belonged to the Gateway move to the Spring Boot application or deployment edge.

| Responsibility | Monolith Location |
|---|---|
| JWT validation | Spring Security filter chain |
| Coarse RBAC | Spring Security authorization rules |
| Fine-grained authorization | Module/use-case layer |
| CORS | Spring Web/Security configuration |
| Client identity-header stripping | Application filter / controller boundary |
| Correlation/request ID | Servlet/observation filter |
| Structured request logging | Application observability |
| Standardized errors | Global exception handling / Problem Details |
| Rate limiting | Optional application or deployment edge |
| TLS termination | Reverse proxy/load balancer/application depending deployment |
| Request-size limits | Web server/security configuration |

Routing between backend services is no longer required.

---

# 20. CORS

CORS must be configured by the monolithic application because the browser now communicates directly with Spring Boot.

Example development policy:

```text
Allowed origin: http://localhost:5173
Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
Allowed headers: Authorization, Content-Type, Idempotency-Key
```

Production origins must be explicitly allowlisted.

Do not use:

```text
Access-Control-Allow-Origin: *
```

for an authenticated production deployment.

CORS is a browser-origin policy, not an authorization mechanism. Backend authorization must still reject unauthorized requests.

---

# 21. Request Filtering and Identity Headers

The monolith must not trust client-supplied identity headers.

Headers such as:

```http
X-User-Id
X-User-Role
```

are never authoritative when supplied by the browser.

The application derives identity from the validated JWT and Spring Security `Authentication`.

If a deployment edge adds an internal correlation/request header, the application should validate or normalize it to avoid uncontrolled log injection.

---

# 22. Rate Limiting

Rate limiting is not a mandatory core feature for the two-hour implementation.

If implemented, it belongs at either:

- the Spring Boot filter/application edge; or
- an external reverse proxy/load balancer/WAF.

Priority targets include:

- authentication-related endpoints where exposed;
- checkout;
- product mutation endpoints;
- expensive administrative operations.

Rate limiting is not a replacement for authorization.

---

# 23. CSRF Decision

The baseline uses:

```http
Authorization: Bearer <JWT>
```

and does not rely on browser-managed authentication cookies for API authorization.

Therefore traditional cookie-based CSRF protection is not part of the baseline API model.

This decision must be revisited if authentication changes to browser-managed cookies or server-side sessions.

The architecture explicitly records this assumption rather than treating “monolith” as equivalent to “no CSRF risk.”

---

# 24. Frontend Security

## 24.1 Route guards

Vue route guards may protect user experience:

```javascript
{
  path: '/admin',
  meta: {
    requiresAuth: true,
    requiresRole: ['ADMIN']
  }
}
```

However:

> **Frontend route guards are not security boundaries.**

Every protected API operation must be independently authorized by the backend.

## 24.2 Role-aware UI

The frontend may hide controls unavailable to a user, but this only improves usability.

An attacker can directly call the API, so backend authorization remains mandatory.

---

# 25. XSS and Browser Credential Safety

The Vue application should:

- use Vue's normal template escaping;
- avoid `v-html` for untrusted content;
- keep access tokens out of `localStorage`;
- apply an appropriate Content Security Policy where practical;
- validate/sanitize untrusted input where applicable.

No client-side control should be treated as a substitute for backend authorization.

---

# 26. Secrets and Configuration

Environment-specific values should not be hard-coded into source code.

Examples:

```text
KEYCLOAK_ISSUER_URI
KEYCLOAK_CLIENT_ID
MONGODB_URI
```

A browser SPA must not receive a confidential client secret.

Local development secrets may use an ignored `.env` or equivalent local configuration.

Never commit production credentials to Git.

A production deployment may use a dedicated secrets-management system, but this is outside the two-hour baseline.

---

# 27. Keycloak Issuer and Docker Networking

The application must establish one canonical Keycloak issuer before implementation.

For example, these may be different network addresses:

```text
Browser-facing:
http://localhost:8180

Docker-internal:
http://keycloak:8080
```

The JWT `iss` claim must match the issuer configured for Spring Security exactly.

Do not substitute a Docker-only hostname into the issuer configuration simply because it is convenient inside Compose.

Before application authorization is debugged, verify:

1. Keycloak starts.
2. Vue can reach the configured Keycloak endpoint.
3. A real access token is issued.
4. The token's `iss` is known.
5. Spring Security's issuer configuration matches it.
6. JWKS retrieval works from the backend.
7. The token validates successfully.

---

# 28. Security Error Semantics

The security layer should produce predictable responses:

```text
401 Unauthorized
→ Missing, invalid, expired, or otherwise unacceptable authentication

403 Forbidden
→ Authenticated but insufficient role/permission

404 Not Found
→ Requested resource does not exist or, for protected user-owned resources,
  must not be disclosed to an unauthorized user
```

Security failures must not expose stack traces, JWT contents, client secrets, or internal implementation details.

A common Problem Details error format can be used across the application.

---

# 29. Security Logging and Audit

The monolith retains operational/security logging even though distributed tracing is no longer required for internal module hops.

Useful fields include:

- timestamp;
- request ID/correlation ID;
- endpoint;
- HTTP method;
- response status;
- authenticated subject where appropriate;
- role/authorization outcome;
- order ID where relevant;
- idempotency context where safe;
- exception/security-event category.

Never log:

- access tokens;
- refresh tokens;
- passwords;
- client secrets;
- sensitive personal data unnecessarily.

Sensitive operations worth auditing include:

- administrative product mutations;
- checkout;
- authorization failures;
- suspicious access attempts;
- security configuration changes where applicable.

---

# 30. Actuator and Diagnostic Endpoints

Operational endpoints must not become an unintended administrative interface.

Only necessary health/readiness endpoints should be exposed publicly or through an authenticated deployment-management path.

Endpoints exposing environment, mappings, beans, metrics, or configuration should remain restricted or disabled unless explicitly required.

The `DEVELOPER` role must not automatically imply unrestricted Spring Actuator access.

---

# 31. Security Testing Strategy

Security testing follows the monolithic runtime model.

## Critical tests

| Test | Expected result |
|---|---|
| No Authorization header | `401 Unauthorized` |
| Malformed JWT | `401 Unauthorized` |
| Expired JWT | `401 Unauthorized` |
| Wrong issuer | `401 Unauthorized` |
| Wrong audience | `401 Unauthorized` |
| USER → ADMIN product mutation | `403 Forbidden` |
| ADMIN → admin product mutation | Success |
| USER accesses own cart | Success |
| User A accesses User B cart | Rejected |
| User A accesses User B order | `404 Not Found` |
| Forged `X-User-Id` | Ignored/rejected |
| Forged role header | Ignored/rejected |
| Client-supplied `userId` differing from JWT subject | Rejected/ignored |
| Modified client-side price | Server does not trust it |
| Duplicate checkout request | No duplicate order |
| Two concurrent same-key checkouts | One committed logical order |
| Cart-version mismatch during checkout | `409 Conflict` |
| Unauthenticated protected module operation | `401 Unauthorized` |

## Medium-priority tests

- DEVELOPER role behavior;
- unauthorized CORS origin;
- mass-assignment attempts;
- oversized input;
- route guard behavior;
- role-aware UI behavior;
- rate limiting if implemented;
- restricted Actuator endpoints;
- security logging behavior.

---

# 32. Security Testing Tools

Appropriate tools include:

- JUnit 5;
- Spring Boot Test;
- MockMvc/WebTestClient where appropriate;
- Keycloak-backed integration tests where practical;
- Postman/curl for manual verification;
- Playwright for the authenticated browser journey.

For the two-hour implementation, prioritize automated authentication/RBAC/ownership tests and the golden-path E2E flow over exhaustive UI security testing.

---

# 33. Relationship to Core Modular Architecture

Group 1 defines:

```text
Vue
 ↓
Spring Boot Modular Monolith
 │
 ├── Product
 ├── Cart
 ├── Order
 └── Checkout
 ↓
MongoDB
```

This security architecture adds:

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
Product / Cart / Order / Checkout authorization
```

The following Group 1 decisions remain security invariants:

- `JWT.sub` is the authenticated user identity;
- `/api/carts/me` uses authenticated ownership;
- Order ownership is enforced server-side;
- client-supplied identity is never authoritative;
- checkout idempotency remains required;
- module APIs are preferred over direct repository access;
- Cart and Order security checks are not bypassed because modules share one JVM.

There is no `/internal/carts/{userId}` endpoint in the monolithic architecture.

---

# 34. What Consolidation Removes From the Original Security Model

The following microservice-specific security mechanisms are intentionally removed:

```text
Gateway → Service JWT validation chain
Service-level resource-server duplication
JWT forwarding between backend services
Service-to-service authentication
Internal REST endpoint authentication
Internal `/internal/**` API exposure rules
mTLS between Product/Cart/Order
Client-credentials service calls
Per-service security configuration copies
```

The security model becomes simpler because the authenticated principal is already available inside one application request context.

However, the following remain:

```text
OIDC authentication
JWT validation
RBAC
ownership/IDOR checks
server-side authorization
secure token handling
CORS
security logging
security testing
secrets management
input validation
```

---

# 35. Security Design Risks and Mitigations

## 35.1 Single-process blast radius

All modules execute within one JVM and share the process boundary.

A serious vulnerability in one module can have broader consequences than in independently isolated services.

Mitigations:

- strict module boundaries;
- least-privilege authorization;
- repository ownership;
- architecture tests;
- secure coding practices;
- dependency vulnerability scanning;
- monitoring.

## 35.2 Module privilege-boundary erosion

Because modules can call one another directly, developers may bypass public APIs.

Mitigations:

- explicit `api` and `internal` packages;
- architecture tests using Spring Modulith or ArchUnit;
- review rules forbidding cross-module repository access;
- module-level ownership tests.

## 35.3 Shared persistence access

One MongoDB deployment does not grant unrestricted cross-module data access.

Each module remains responsible for its owned collections, and ownership checks must remain in the application layer.

## 35.4 Gateway removal

Removing the Gateway eliminates centralized routing and some edge controls.

Mitigations:

- application-level Spring Security;
- strict CORS;
- request filtering;
- optional rate limiting;
- secure reverse-proxy/load-balancer configuration where deployed;
- restricted Actuator endpoints.

---

# 36. 2-Hour Security Scope

## Must implement

1. Keycloak Docker setup.
2. `shopping-cart` realm.
3. Vue OIDC Authorization Code + PKCE login.
4. Public SPA client configuration.
5. JWT access tokens.
6. Single Spring Security JWT validation boundary.
7. Signature, issuer, expiration, and audience validation.
8. `USER`, `ADMIN`, `DEVELOPER` roles.
9. Keycloak-to-Spring role mapping.
10. `/me` ownership model.
11. Server-side order/cart ownership checks.
12. `@PreAuthorize` or equivalent module/use-case authorization.
13. Application CORS configuration.
14. Environment-based security configuration.
15. Critical authentication/RBAC/IDOR tests.

## Implement if time permits

- developer/tester security UI;
- rate limiting;
- additional negative security tests;
- CSP hardening;
- detailed security audit logging;
- Keycloak/Testcontainers integration verification.

## Document only / future work

- mTLS;
- OAuth2 Client Credentials service identities;
- service mesh;
- production secrets manager;
- token revocation infrastructure;
- SIEM;
- advanced fraud detection;
- Kubernetes network policies;
- multi-vendor `STORE_OWNER` model.

---

# 37. Final Security Architecture

```text
                         ┌──────────────────────┐
                         │       Keycloak       │
                         │ OIDC / OAuth 2.0     │
                         │ Users / Roles / JWT  │
                         └──────────┬───────────┘
                                    │
                            Authorization Code
                                  + PKCE
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │       Vue SPA        │
                         │   public client      │
                         │ access token memory  │
                         └──────────┬───────────┘
                                    │
                            Authorization: Bearer JWT
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Monolith                       │
│                                                                 │
│  Spring Security                                                 │
│  ├── JWT signature validation                                   │
│  ├── issuer validation                                          │
│  ├── expiration validation                                      │
│  ├── audience validation                                        │
│  └── role mapping / route authorization                         │
│                                                                 │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌─────────────┐ │
│  │  Product   │ │    Cart    │ │   Order    │ │  Checkout   │ │
│  │  module    │ │   module   │ │   module   │ │   module    │ │
│  └────────────┘ └────────────┘ └────────────┘ └─────────────┘ │
│        │               │              │              │           │
│        └────── module-level authorization / ownership ──────────┘
└───────────────────────────────────────┬─────────────────────────┘
                                        │
                                        ▼
                                   MongoDB
```

### Security principle

> **Authenticate once at the application boundary, authorize at the protected use case/resource owner, derive identity from the validated security context, and never trust client-controlled identity or business-security data.**

---

# 38. Final Security Decisions

## Locked

- Keycloak as Identity Provider.
- OIDC.
- Authorization Code + PKCE.
- Public Vue SPA client.
- JWT access tokens for API authorization.
- In-memory access-token storage.
- Single Spring Security JWT validation boundary.
- Signature, issuer, expiration, and audience validation.
- `USER`, `ADMIN`, `DEVELOPER` roles.
- JWT `sub` as canonical authenticated identity.
- Server-side RBAC.
- Server-side resource ownership checks.
- `/me` ownership APIs.
- No client-trusted identity headers.
- No backend service-to-service authentication.
- No JWT forwarding between backend modules.
- No internal HTTP security endpoints.
- Strict CORS configuration.
- Backend security remains authoritative over frontend UI.

## Deferred / optional

- Rate limiting implementation.
- Detailed security auditing.
- CSP hardening.
- Keycloak/Testcontainers integration verification.
- Token revocation infrastructure.
- Production secrets management.

## Out of scope

- mTLS.
- Service mesh.
- OAuth2 client-credentials service identity.
- Multi-vendor seller security.
- Advanced SIEM/fraud infrastructure.

---

# 39. Status

**GROUP 2 — MONOLITHIC SECURITY ARCHITECTURE: CLAUDE REVIEWED — MINOR CHANGES APPLIED; READY FOR FOLLOW-UP REVIEW**

This document is the dedicated security architecture for the modular monolith. It is intentionally separate from the core module/checkout architecture and removes security mechanisms whose only purpose was protecting microservice-to-microservice network boundaries while retaining application authentication, authorization, ownership, and security invariants.

## Research basis

This architecture was derived from:

- the original Group 2 microservice security architecture;
- the Group 1 READY modular-monolith architecture;
- the Perplexity security and master-architecture findings;
- the Gemini consolidated monolithic-architecture research.

The research inputs are treated as design evidence; this document is the resulting security architecture candidate for follow-up independent review.

## Final Verification Checklist

Before deployment, verify:

- Keycloak access tokens contain the expected `shopping-cart-api` audience through explicit realm/client configuration.
- The JWT role converter correctly extracts nested `realm_access.roles`.
- The selected Vue OIDC library's refresh-token storage behavior is known, documented, and not silently persisted in insecure browser storage.
- `GET /api/admin/orders/{orderId}` is protected by `ROLE_ADMIN`.
