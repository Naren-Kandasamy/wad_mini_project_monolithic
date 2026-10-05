# Track 2 Implementation Walkthrough

**Branch:** `feature/track2-security-platform`  
**Reference:** [architecture-monolith-grp2-ready.md](file:///home/sarav/Projects/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md)  
**Final test result:** 25 tests, 0 failures, 0 errors  

---

## Modifications

### Phase 1 — Keycloak Realm Configuration
- **`infra/keycloak/realm-export.json`** — Automated realm import file:
  - Realm: `shopping-cart`, SSL: none (local dev)
  - Roles: `USER`, `ADMIN`, `DEVELOPER`
  - Client: `shopping-cart-spa` — public client, PKCE (`S256`), redirect URIs `http://localhost:5173/*`
  - Client scope: `shopping-cart-audience` with Audience Mapper emitting `shopping-cart-api` in the access token `aud` claim
  - Seed users: `user1` (USER), `admin1` (ADMIN + USER), `dev1` (DEVELOPER)

### Phase 2 — Maven Infrastructure
- **`backend/pom.xml`** — Spring Boot 3.3.4 parent, Java 21, dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-security`
  - `spring-boot-starter-oauth2-resource-server`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-actuator`
  - `spring-security-test` (test scope)
- **`backend/src/main/java/com/example/shoppingcart/Application.java`** — `@SpringBootApplication` entry point
- **`backend/src/main/resources/application.yml`** — `KEYCLOAK_ISSUER_URI` env var, audience `shopping-cart-api`, CORS origin allowlist

### Phase 3 — JWT Security Configuration
- **`security/AudienceValidator.java`** — `OAuth2TokenValidator<Jwt>` checking that `shopping-cart-api` appears in the token `aud` claim; returns `invalid_token` error with descriptive message on failure
- **`security/KeycloakJwtAuthenticationConverter.java`** — `Converter<Jwt, AbstractAuthenticationToken>` extracting the nested `realm_access.roles` array and mapping each entry to a `ROLE_*` `SimpleGrantedAuthority`; returns empty collection (not a failure) when `realm_access` is absent
- **`security/SecurityConfig.java`** — `@EnableWebSecurity @EnableMethodSecurity` bean:
  - `SecurityFilterChain`: CSRF disabled (bearer-token model), stateless sessions, custom `401` entrypoint emitting `application/problem+json`
  - OAuth2 resource server with `KeycloakJwtAuthenticationConverter`
  - `@ConditionalOnMissingBean(JwtDecoder.class)` + `DelegatingOAuth2TokenValidator` (issuer + audience) on the real decoder; allows test `@MockBean` override without override exception
  - Route RBAC: `GET /api/products/**` → `permitAll()`, mutations → `hasRole('ADMIN')`, `/api/admin/**` → `hasRole('ADMIN')`, `/api/carts/me/**` → `hasRole('USER')`, `/api/orders/**` → `authenticated()`
  - `CorsConfigurationSource`: origin `http://localhost:5173`, methods `GET POST PUT DELETE OPTIONS PATCH`, headers include `Authorization Content-Type Idempotency-Key X-Request-Id`

### Phase 4 — Identity Utility
- **`security/SecurityUtils.java`** — Static utility with two methods:
  - `getAuthenticatedUserId()` — extracts `Jwt.getSubject()` from `JwtAuthenticationToken`; throws `AccessDeniedException` if no principal, preventing any code path from relying on a client-supplied value
  - `hasRole(String role)` — checks `GrantedAuthority` collection, handles `ROLE_` prefix normalisation

### Phase 5 — Shared Error Infrastructure
- **`shared/error/ResourceNotFoundException.java`** — Domain 404 exception (unchecked)
- **`shared/error/ConflictException.java`** — Domain 409 exception (unchecked)
- **`shared/error/GlobalExceptionHandler.java`** — `@RestControllerAdvice` RFC 7807/9457 `ProblemDetail` handler:
  - `AccessDeniedException` → 403 `Forbidden`
  - `AuthenticationException` → 401 `Unauthorized`
  - `ResourceNotFoundException` → 404 `Not Found`
  - `ConflictException` → 409 `Conflict`
  - `MethodArgumentNotValidException` → 400 `Bad Request` with `invalidParams` map
  - `NoResourceFoundException` (Spring 6) → 404 `Not Found` _(prevents framework route-miss being swallowed as 500)_
  - `Exception` catch-all → 500 with fixed safe message `"An unexpected server error occurred."` — no stack trace, no internal detail

### Phase 6 — Correlation ID Filter
- **`shared/filter/CorrelationIdFilter.java`** — `OncePerRequestFilter` at `HIGHEST_PRECEDENCE`:
  - Reads `X-Request-Id` header; generates UUID v4 if absent
  - Binds value to SLF4J `MDC` key `requestId` for structured log correlation
  - Sets `X-Request-Id` on the response
  - Clears MDC in `finally` block to prevent thread-pool leakage

---

## Test Documentation

### Test Suite Summary

| Test Class | Tests | Status |
|---|---|---|
| `SecurityComponentsTest` | 4 | PASSED |
| `SecurityUtilsTest` | 2 | PASSED |
| `GlobalExceptionHandlerTest` | 5 | PASSED |
| `CorrelationIdFilterTest` | 2 | PASSED |
| `SecurityIntegrationTest` | 12 | PASSED |
| **TOTAL** | **25** | **25/25** |

### SecurityComponentsTest (4 tests)

| # | Test Name | Assertion |
|---|---|---|
| 1 | `shouldExtractRealmRoles` | Converter maps `realm_access.roles: [USER, ADMIN]` → `ROLE_USER`, `ROLE_ADMIN` |
| 2 | `shouldHandleMissingRolesCleanly` | Converter returns empty authorities when `realm_access` claim absent |
| 3 | `shouldPassWhenExpectedAudiencePresent` | `AudienceValidator("shopping-cart-api")` succeeds for token with `aud: [shopping-cart-api, account]` |
| 4 | `shouldFailWhenExpectedAudienceMissing` | `AudienceValidator` fails with `invalid_token` error when `shopping-cart-api` not in aud |

### SecurityUtilsTest (2 tests)

| # | Test Name | Assertion |
|---|---|---|
| 1 | `shouldExtractUserIdFromJwtSubject` | `getAuthenticatedUserId()` returns `jwt.getSubject()` from `JwtAuthenticationToken` |
| 2 | `shouldThrowWhenUnauthenticated` | `getAuthenticatedUserId()` throws `AccessDeniedException` when `SecurityContext` is empty |

### GlobalExceptionHandlerTest (5 tests)

| # | Test Name | HTTP Status | ProblemDetail Title |
|---|---|---|---|
| 1 | `shouldHandleAccessDeniedException` | 403 | Forbidden |
| 2 | `shouldHandleAuthenticationException` | 401 | Unauthorized |
| 3 | `shouldHandleResourceNotFound` | 404 | Not Found |
| 4 | `shouldHandleConflictException` | 409 | Conflict |
| 5 | `shouldHandleGenericExceptionSafely` | 500 | Detail = "An unexpected server error occurred." — no internal message leaked |

### CorrelationIdFilterTest (2 tests)

| # | Test Name | Assertion |
|---|---|---|
| 1 | `shouldPropagateIncomingCorrelationId` | Incoming `X-Request-Id` header is echoed to response and bound to MDC; MDC cleared after chain |
| 2 | `shouldGenerateNewCorrelationIdWhenMissing` | UUID is generated, set on response, bound to MDC when no header present |

### SecurityIntegrationTest (12 tests) — `@SpringBootTest` + MockMvc

| # | Test Name | Method + Path | Token/Role | Expected |
|---|---|---|---|---|
| 1 | `getProductsShouldBePublic` | GET `/api/products` | None | 200 OK |
| 2 | `postProductUnauthenticatedShouldReturn401` | POST `/api/products` | None | 401 Unauthorized |
| 3 | `postProductAsUserShouldReturn403` | POST `/api/products` | ROLE_USER | 403 Forbidden |
| 4 | `postProductAsAdminShouldReturn200` | POST `/api/products` | ROLE_ADMIN | 200 OK |
| 5 | `getCartMeUnauthenticatedShouldReturn401` | GET `/api/carts/me` | None | 401 Unauthorized |
| 6 | `getCartMeShouldDeriveIdentityFromJwtSubjectIgnoringForgedHeader` | GET `/api/carts/me` | sub=real-user-123 + forged X-User-Id | 200; body userId=real-user-123 |
| 7 | `getOrderForeignUserShouldReturn404` | GET `/api/orders/order-1` | sub=user-bob | 404; title Not Found |
| 8 | `getOrderOwnerShouldSucceed` | GET `/api/orders/order-1` | sub=user-alice | 200; orderId=order-1 |
| 9 | `getAdminStatsAsUserShouldReturn403` | GET `/api/admin/stats` | ROLE_USER | 403 Forbidden |
| 10 | `getAdminStatsAsAdminShouldReturn200` | GET `/api/admin/stats` | ROLE_ADMIN | 200 OK |
| 11 | `corsPreflightFromAllowedOriginShouldSucceed` | OPTIONS `/api/products` | Origin: localhost:5173 | 200; Access-Control-Allow-Origin header |
| 12 | `unauthenticatedRequestReturnsProblemDetailBody` | GET `/api/orders/some-id` | None | 401; Content-Type: application/problem+json |

---

## Incident Log

| # | Phase | What Failed | Error | Engineering Fix Applied |
|---|---|---|---|---|
| 1 | SecurityIntegrationTest — JSON matchers | Compile error on `jsonPath(...).isEqualTo(...)` | `cannot find symbol: method isEqualTo(String) — location: JsonPathResultMatchers` | `isEqualTo()` is an AssertJ method; MockMvc's `jsonPath` uses `.value()`. Replaced all occurrences. |
| 2 | SecurityIntegrationTest — Context load | `BeanDefinitionOverrideException` on `jwtDecoder` | Test's `@Primary JwtDecoder` bean collided with `SecurityConfig.jwtDecoder()` bean | Added `@ConditionalOnMissingBean(JwtDecoder.class)` to `SecurityConfig.jwtDecoder()`. Changed test to declare `@MockBean JwtDecoder` and enabled `spring.main.allow-bean-definition-overriding=true`. |
| 3 | SecurityIntegrationTest — 500 on all controller-layer tests | Inner static `@RestController` classes not registered as beans | `@SpringBootTest` scans main-source packages only; test-source `@RestController` inner classes are invisible to component scan. Spring 6 then throws `NoResourceFoundException` for all unhandled routes, which `@ExceptionHandler(Exception.class)` swallows as 500. | (a) Added `@Import({DummyProductController.class, ...})` on the test class — `@Import` explicitly registers the class as a bean regardless of component scan. (b) Added dedicated `@ExceptionHandler(NoResourceFoundException.class)` in `GlobalExceptionHandler` returning 404 so the catch-all 500 handler is no longer reached for route-miss cases. |
| 4 | GlobalExceptionHandler — compile error | `@ExceptionHandler(ErrorResponse.class)` fails to compile | `org.springframework.web.ErrorResponse` is an interface, not a `Throwable` subtype; `@ExceptionHandler` requires `Throwable` | Removed the `ErrorResponse` handler entirely. `NoResourceFoundException` (a concrete `Throwable`) already covers all observed Spring 6 route-miss scenarios. Unused `ErrorResponse` import also removed. |
