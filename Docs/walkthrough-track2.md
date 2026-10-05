# Track 2 Implementation Walkthrough

**Branch:** `feature/track2-security-platform`
**Stack:** Spring Boot 3.3.4 · Java 21 · Keycloak 24+
**Scope reference:** `Docs/architecture-monolith-grp2-ready.md`
**Final test result:** 25 / 25 tests passing — 0 failures — BUILD SUCCESS

---

## Overview

Track 2 delivers the complete **Security & Platform API** layer for the modular monolith.
It implements:

1. Keycloak realm setup (roles, public PKCE client, audience mapper, seed users)
2. Spring Security JWT resource-server boundary (one validation point for the whole monolith)
3. Keycloak → Spring role mapping (`realm_access.roles` → `ROLE_*`)
4. Audience validation enforcing `shopping-cart-api`
5. Route-level RBAC (product catalog public, mutations admin-only, cart/orders authenticated)
6. Server-authoritative identity — `Jwt.sub` only; forged headers rejected
7. IDOR protection — foreign order access returns `404 Not Found`, not data
8. Application CORS policy (`http://localhost:5173`)
9. RFC 7807 / 9457 `ProblemDetail` global error handler (no stack trace leakage)
10. `X-Request-Id` correlation filter with SLF4J MDC binding

---

## Modifications

### `infra/keycloak/realm-export.json`

Automated Keycloak realm import file. Drop this into a Keycloak container on startup
with `--import-realm` to provision the entire realm without manual UI steps.

| Setting | Value |
|---|---|
| Realm name | `shopping-cart` |
| SSL required | `none` (local dev) |
| Realm roles | `USER`, `ADMIN`, `DEVELOPER` |
| SPA client ID | `shopping-cart-spa` |
| Client type | Public (no secret) |
| PKCE method | `S256` |
| Redirect URIs | `http://localhost:5173/*` |
| Web origins | `http://localhost:5173` |
| Audience mapper | Emits `shopping-cart-api` in access token `aud` claim |
| Seed user — user1 | password `password123`, role `USER` |
| Seed user — admin1 | password `admin123`, roles `ADMIN` + `USER` |
| Seed user — dev1 | password `dev123`, role `DEVELOPER` |

---

### `backend/pom.xml`

Spring Boot 3.3.4 Maven project (Java 21).

Key dependencies:

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API / Spring MVC |
| `spring-boot-starter-security` | Spring Security |
| `spring-boot-starter-oauth2-resource-server` | JWT validation, Bearer token support |
| `spring-boot-starter-validation` | Bean Validation (JSR-380) |
| `spring-boot-starter-actuator` | `/actuator/health` liveness/readiness |
| `spring-security-test` (test) | `jwt()` MockMvc post-processor |

---

### `backend/src/main/resources/application.yml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/shopping-cart}

app:
  security:
    expected-audience: shopping-cart-api
  cors:
    allowed-origins:
      - http://localhost:5173
```

The issuer URI is environment-variable driven (`KEYCLOAK_ISSUER_URI`).
No credentials are hard-coded.

---

### `security/AudienceValidator.java`

Implements `OAuth2TokenValidator<Jwt>`.

**Logic:**
- Reads the `aud` claim list from the incoming JWT
- Returns `success()` if `shopping-cart-api` is present
- Returns `failure(invalid_token)` with a descriptive message otherwise

An otherwise-valid Keycloak token issued for a different resource server is rejected.

---

### `security/KeycloakJwtAuthenticationConverter.java`

Implements `Converter<Jwt, AbstractAuthenticationToken>`.

**Logic:**
1. Read `realm_access` map claim from JWT
2. Extract the `roles` list inside it
3. Map each role string to `SimpleGrantedAuthority("ROLE_" + role)`
4. Return `JwtAuthenticationToken(jwt, authorities, jwt.getSubject())`

Handles `null` or missing `realm_access` gracefully — returns empty authority set
rather than throwing, so anonymous public routes still work.

This replaces Spring's default `JwtGrantedAuthoritiesConverter`, which cannot navigate
Keycloak's nested `realm_access.roles` structure without a custom converter.

---

### `security/SecurityConfig.java`

`@EnableWebSecurity @EnableMethodSecurity` configuration class.

#### `SecurityFilterChain` (route authorization)

```
GET  /api/products/**         → permitAll()          (public catalog browsing)
POST /api/products/**         → hasRole("ADMIN")
PUT  /api/products/**         → hasRole("ADMIN")
DELETE /api/products/**       → hasRole("ADMIN")
/api/admin/**                 → hasRole("ADMIN")
/api/dev/**                   → hasRole("DEVELOPER")
/api/carts/me/**              → hasRole("USER")
/api/orders/**                → authenticated()
/actuator/health/**, /actuator/info → permitAll()
everything else               → authenticated()
```

Other settings:
- **CSRF disabled** — pure bearer-token API; no browser session cookies
- **Stateless sessions** — no `HttpSession` created
- **Custom 401 entrypoint** — returns `application/problem+json` instead of Spring's default redirect
- **`KeycloakJwtAuthenticationConverter`** wired to the JWT resource server
- **`@ConditionalOnMissingBean(JwtDecoder.class)`** on the real decoder bean — allows `@MockBean` in tests without `BeanDefinitionOverrideException`

#### `CorsConfigurationSource`

| Setting | Value |
|---|---|
| Allowed origin | `http://localhost:5173` |
| Allowed methods | `GET POST PUT DELETE OPTIONS PATCH` |
| Allowed headers | `Authorization Content-Type Idempotency-Key X-Request-Id` |
| Exposed headers | `X-Request-Id` |
| Allow credentials | `true` |

#### `JwtDecoder` (real, production)

`DelegatingOAuth2TokenValidator` composed of:
1. `JwtValidators.createDefaultWithIssuer(issuerUri)` — signature + issuer + timestamps
2. `AudienceValidator("shopping-cart-api")` — audience enforcement

---

### `security/SecurityUtils.java`

Static utility for server-authoritative identity extraction.

#### `getAuthenticatedUserId()`

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
// throws AccessDeniedException if null / anonymous
Jwt jwt = ((JwtAuthenticationToken) auth).getToken();
return jwt.getSubject();   // JWT sub claim — the canonical user ID
```

Every module that needs the caller's identity calls this method.
No controller, service, or repository ever reads `userId` from:
- request body
- query parameter
- `X-User-Id` header
- `X-User-Role` header

Those client-supplied values are silently ignored.

#### `hasRole(String role)`

Checks the `GrantedAuthority` set of the current authentication.
Normalises the `ROLE_` prefix automatically.

---

### `shared/error/ResourceNotFoundException.java`

Unchecked exception for "resource not found" domain conditions.
Mapped to `404 Not Found` by `GlobalExceptionHandler`.
Used specifically by order ownership checks to return 404 (not 403)
for IDOR — a caller cannot distinguish a foreign order from a non-existent one.

---

### `shared/error/ConflictException.java`

Unchecked exception for domain conflict conditions
(e.g., cart version mismatch, duplicate idempotency key race).
Mapped to `409 Conflict` by `GlobalExceptionHandler`.

---

### `shared/error/GlobalExceptionHandler.java`

`@RestControllerAdvice` — RFC 7807 / 9457 `ProblemDetail` response for all exception types.

| Exception | HTTP Status | Title |
|---|---|---|
| `AccessDeniedException` | 403 | Forbidden |
| `AuthenticationException` | 401 | Unauthorized |
| `ResourceNotFoundException` | 404 | Not Found |
| `ConflictException` | 409 | Conflict |
| `MethodArgumentNotValidException` | 400 | Bad Request + `invalidParams` map |
| `NoResourceFoundException` (Spring 6) | 404 | Not Found |
| `Exception` (catch-all) | 500 | Fixed: `"An unexpected server error occurred."` |

**Security property:** The 500 catch-all handler logs the full exception internally but
returns only the fixed safe message. No stack traces, class names, SQL, credentials,
or internal detail are ever serialised into the response body.

---

### `shared/filter/CorrelationIdFilter.java`

`OncePerRequestFilter` registered at `Ordered.HIGHEST_PRECEDENCE`
(runs before Spring Security's filter chain).

**Behaviour:**
1. Read `X-Request-Id` request header
2. If absent → generate `UUID.randomUUID().toString()`
3. Put the value into `MDC.put("requestId", correlationId)`
4. Set `X-Request-Id` on the response
5. Call `filterChain.doFilter(...)` inside a `try/finally`
6. `finally` → `MDC.remove("requestId")` — prevents MDC bleed across thread-pool reuse

The `requestId` key can be included in the logging pattern to correlate all log lines
for a single HTTP request.

---

## Test Documentation

### Summary

| Test Class | Tests | Result |
|---|---|---|
| `SecurityComponentsTest` | 4 | PASSED |
| `SecurityUtilsTest` | 2 | PASSED |
| `GlobalExceptionHandlerTest` | 5 | PASSED |
| `CorrelationIdFilterTest` | 2 | PASSED |
| `SecurityIntegrationTest` | 12 | PASSED |
| **TOTAL** | **25** | **25 / 25** |

---

### SecurityComponentsTest — 4 tests

Pure unit tests. No Spring context. Instantiate classes directly.

| # | Test | What is asserted |
|---|---|---|
| 1 | `shouldExtractRealmRoles` | Converter receives JWT with `realm_access.roles: [USER, ADMIN]`; resulting authorities are exactly `ROLE_USER` and `ROLE_ADMIN` |
| 2 | `shouldHandleMissingRolesCleanly` | JWT with no `realm_access` claim → authorities collection is empty, no exception thrown |
| 3 | `shouldPassWhenExpectedAudiencePresent` | `AudienceValidator("shopping-cart-api")` validates successfully when `aud: [shopping-cart-api, account]` |
| 4 | `shouldFailWhenExpectedAudienceMissing` | `AudienceValidator` returns `invalid_token` OAuth2 error when `aud: [other-api]` |

---

### SecurityUtilsTest — 2 tests

Pure unit tests. Manually set and clear `SecurityContextHolder`.

| # | Test | What is asserted |
|---|---|---|
| 1 | `shouldExtractUserIdFromJwtSubject` | `SecurityUtils.getAuthenticatedUserId()` returns `jwt.getSubject()` (`"verified-user-uuid-99"`) from a populated `JwtAuthenticationToken` |
| 2 | `shouldThrowWhenUnauthenticated` | Calling `getAuthenticatedUserId()` with an empty `SecurityContext` throws `AccessDeniedException` with message containing `"No authenticated principal"` |

---

### GlobalExceptionHandlerTest — 5 tests

Pure unit tests. Instantiate `GlobalExceptionHandler` directly, call handler methods.
No Spring context or MockMvc needed.

| # | Test | Input | Expected status | Expected title | Notes |
|---|---|---|---|---|---|
| 1 | `shouldHandleAccessDeniedException` | `AccessDeniedException("Access is denied")` | 403 | `Forbidden` | Detail equals exception message |
| 2 | `shouldHandleAuthenticationException` | Anonymous `AuthenticationException` | 401 | `Unauthorized` | — |
| 3 | `shouldHandleResourceNotFound` | `ResourceNotFoundException("Order not found")` | 404 | `Not Found` | Detail equals exception message |
| 4 | `shouldHandleConflictException` | `ConflictException("Cart version conflict")` | 409 | `Conflict` | Detail equals exception message |
| 5 | `shouldHandleGenericExceptionSafely` | `RuntimeException("Sensitive database credentials error inside stack trace")` | 500 | `Internal Server Error` | Detail must equal `"An unexpected server error occurred."` — internal message must NOT appear in response |

---

### CorrelationIdFilterTest — 2 tests

Unit tests using `MockHttpServletRequest` / `MockHttpServletResponse` and a lambda `FilterChain`.
No Spring context.

| # | Test | Scenario | Assertions |
|---|---|---|---|
| 1 | `shouldPropagateIncomingCorrelationId` | Request carries `X-Request-Id: custom-req-id-1234` | Response header equals `custom-req-id-1234`; MDC value during chain execution equals same string; MDC cleared after chain completes |
| 2 | `shouldGenerateNewCorrelationIdWhenMissing` | Request has no `X-Request-Id` header | Response header is non-blank UUID; MDC value during chain equals that UUID; MDC cleared after chain completes |

---

### SecurityIntegrationTest — 12 tests

Full `@SpringBootTest` + `MockMvc`. Real `SecurityConfig` loaded.
`JwtDecoder` replaced with `@MockBean` (no live Keycloak needed).
Dummy `@RestController` inner classes registered via `@Import`.
JWT tokens injected via `SecurityMockMvcRequestPostProcessors.jwt()`.

| # | Test | Method + Path | Auth / Role | Expected status | Extra assertion |
|---|---|---|---|---|---|
| 1 | `getProductsShouldBePublic` | GET `/api/products` | None | 200 | — |
| 2 | `postProductUnauthenticatedShouldReturn401` | POST `/api/products` | None | 401 | — |
| 3 | `postProductAsUserShouldReturn403` | POST `/api/products` | `ROLE_USER` | 403 | — |
| 4 | `postProductAsAdminShouldReturn200` | POST `/api/products` | `ROLE_ADMIN` | 200 | — |
| 5 | `getCartMeUnauthenticatedShouldReturn401` | GET `/api/carts/me` | None | 401 | — |
| 6 | `getCartMeShouldDeriveIdentityFromJwtSubject` | GET `/api/carts/me` | `sub=real-user-123` + `ROLE_USER` + forged `X-User-Id: forged-attacker-id` | 200 | Response body `userId` = `real-user-123` (forged header ignored) |
| 7 | `getOrderForeignUserShouldReturn404` | GET `/api/orders/order-1` | `sub=user-bob` + `ROLE_USER` | 404 | Body `status=404`, `title=Not Found` |
| 8 | `getOrderOwnerShouldSucceed` | GET `/api/orders/order-1` | `sub=user-alice` + `ROLE_USER` | 200 | Body `orderId=order-1` |
| 9 | `getAdminStatsAsUserShouldReturn403` | GET `/api/admin/stats` | `ROLE_USER` | 403 | — |
| 10 | `getAdminStatsAsAdminShouldReturn200` | GET `/api/admin/stats` | `ROLE_ADMIN` | 200 | — |
| 11 | `corsPreflightFromAllowedOriginShouldSucceed` | OPTIONS `/api/products` (Origin: `http://localhost:5173`) | None | 200 | `Access-Control-Allow-Origin` = `http://localhost:5173` |
| 12 | `unauthenticatedRequestReturnsProblemDetailBody` | GET `/api/orders/some-id` | None | 401 | `Content-Type` contains `application/problem+json` |

---

## Incident Log

All failures encountered during the TDD loop, with the exact fix applied.

### Incident 1 — MockMvc `jsonPath` matcher compile error

| Field | Detail |
|---|---|
| **Phase** | SecurityIntegrationTest — first compile attempt |
| **Symptom** | `cannot find symbol: method isEqualTo(String) — location: JsonPathResultMatchers` |
| **Root cause** | `isEqualTo()` is an AssertJ method. Spring MockMvc's `jsonPath(...)` returns a `JsonPathResultMatchers` whose value-assertion method is `.value(x)`, not `.isEqualTo(x)`. |
| **Fix** | Replaced every `jsonPath("$.x").isEqualTo(v)` call with `jsonPath("$.x").value(v)`. |

---

### Incident 2 — `BeanDefinitionOverrideException` on `jwtDecoder`

| Field | Detail |
|---|---|
| **Phase** | SecurityIntegrationTest — Spring context load |
| **Symptom** | `BeanDefinitionOverrideException: Cannot register bean definition for bean 'jwtDecoder' since there is already [factoryBeanName=securityConfig] bound.` |
| **Root cause** | The test's inner `@TestConfiguration` declared a `@Primary @Bean JwtDecoder` that collided with `SecurityConfig.jwtDecoder()`. Spring Boot 3 disallows override by default. |
| **Fix** | (a) Added `@ConditionalOnMissingBean(JwtDecoder.class)` to `SecurityConfig.jwtDecoder()` so it yields to any externally-supplied decoder. (b) Replaced the `@TestConfiguration` bean with a `@MockBean JwtDecoder jwtDecoder` field on the test class — Mockito registers it before the application context is assembled, so `@ConditionalOnMissingBean` sees it and suppresses the real decoder. (c) Added `spring.main.allow-bean-definition-overriding=true` as a safety net. |

---

### Incident 3 — 500 instead of expected statuses on controller-layer tests

| Field | Detail |
|---|---|
| **Phase** | SecurityIntegrationTest — runtime, after context loaded successfully |
| **Symptom** | Six tests expecting 200 / 404 returned 500 with body `{"title":"Internal Server Error","detail":"An unexpected server error occurred."}`. |
| **Root cause** | Two compounding issues: (a) `@SpringBootTest` component-scans `src/main/java` only. Inner static `@RestController` classes declared inside the test class live in `src/test/java` and are invisible to the scan — so no handler existed for those routes. (b) Spring Framework 6.x throws `NoResourceFoundException` when no handler matches. Our catch-all `@ExceptionHandler(Exception.class)` swallowed it and returned 500. |
| **Fix applied — part A** | Added `@Import({DummyProductController.class, DummyCartController.class, DummyOrderController.class, DummyAdminController.class})` to the test class. `@Import` registers the classes as bean definitions explicitly, bypassing component scan. Spring MVC's `RequestMappingHandlerMapping` then detects the `@RequestMapping` annotations on those beans and registers the routes. |
| **Fix applied — part B** | Added a dedicated `@ExceptionHandler(NoResourceFoundException.class)` handler in `GlobalExceptionHandler` that returns `404 Not Found` with a `ProblemDetail` body, preventing it from falling through to the generic 500 handler. |

---

### Incident 4 — `@ExceptionHandler(ErrorResponse.class)` compile error

| Field | Detail |
|---|---|
| **Phase** | `GlobalExceptionHandler` compilation |
| **Symptom** | `incompatible types: Class<ErrorResponse> cannot be converted to Class<? extends Throwable>` |
| **Root cause** | `org.springframework.web.ErrorResponse` is a Spring interface, not a subclass of `Throwable`. `@ExceptionHandler` only accepts `Throwable` subtypes. |
| **Fix** | Removed the `ErrorResponse` handler entirely and its import. `NoResourceFoundException` (a concrete `Throwable`) already covers all observed Spring 6 route-not-found scenarios. |

---

### Incident 5 — walkthrough-track2.md written as 0-byte file

| Field | Detail |
|---|---|
| **Phase** | Documentation |
| **Symptom** | `Docs/walkthrough-track2.md` showed 0 bytes after first write attempt. |
| **Root cause** | The artifact-path write tool used `ArtifactMetadata` pointing outside the artifact directory; the tool rejected the write silently. The git commit captured the empty file. |
| **Fix** | Rewrote using Python `open()` via shell, which writes directly to the project path without artifact-directory restrictions. SecurityIntegrationTest was also re-verified: `mvn clean test` showed 25 / 25 passing before this documentation was committed. |
