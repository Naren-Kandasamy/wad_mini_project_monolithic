# Cumulative Project Walkthrough & Verification Journal

## Living Document Purpose
This walkthrough is the authoritative, phase-by-phase operational journal for the **Shopping Cart Modular Monolith** development. It is updated and appended at the conclusion of every implementation phase to provide full transparency across all teammates and stakeholders.

Every phase entry captures:
1. **Changes Done in That Phase** (Primary focus: files created, modified, architectural integrations completed by Teammates 1, 2, and 3).
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

## Master Phase Progression Tracker

| Phase | Description | Status | Tests Executed | Pass Rate | Unresolved Tech Debt |
|---|---|---|---|---|---|
| **Phase 1** | Foundation, Infrastructure & Contract Freezing | 🟡 Ready to Begin | 0 | - | 0 |
| **Phase 2** | Core Domain, Transactions & Frontend Views | ⚪ Pending | 0 | - | - |
| **Phase 3** | Comprehensive Testing Suite & Concurrency | ⚪ Pending | 0 | - | - |
| **Phase 4** | Observability, Smoke Path & Tech Debt Elimination | ⚪ Pending | 0 | - | - |

---

## Phase 1: Foundation, Infrastructure & Contract Freezing

### 1. Changes Done in Phase 1
*(To be populated as Phase 1 tasks are committed by each teammate)*

#### Teammate 1 (Domain & Core Modules)
- **Files Created:**
  - `backend/src/main/java/com/example/shoppingcart/product/api/ProductApi.java`
  - `backend/src/main/java/com/example/shoppingcart/product/api/ProductSnapshot.java`
  - `backend/src/main/java/com/example/shoppingcart/cart/api/CartApi.java`
  - `backend/src/main/java/com/example/shoppingcart/cart/api/CheckoutCart.java`
  - `backend/src/main/java/com/example/shoppingcart/order/api/OrderApi.java`
  - `backend/src/main/java/com/example/shoppingcart/order/api/OrderSnapshot.java`
  - Document entities: `ProductDocument.java`, `CartDocument.java`, `OrderDocument.java`
- **Architectural Integrations:**
  - Bound in-process typed module API interfaces.
  - Frozen domain models with strict package boundaries (`api` vs `internal`).

#### Teammate 2 (Security & Cross-Cutting Platform)
- **Files Created:**
  - `infra/keycloak/realm-export.json`
  - `backend/src/main/java/com/example/shoppingcart/security/SecurityConfig.java`
  - `backend/src/main/java/com/example/shoppingcart/security/KeycloakJwtAuthenticationConverter.java`
  - `backend/src/main/java/com/example/shoppingcart/shared/filter/CorrelationIdFilter.java`
  - `backend/src/main/java/com/example/shoppingcart/shared/error/GlobalExceptionHandler.java`
- **Architectural Integrations:**
  - Configured Spring Security 6 Resource Server for JWT bearer tokens.
  - Set up audience validator (`shopping-cart-api`) and nested role extraction (`realm_access.roles`).
  - Added MDC request correlation filter and RFC 7807/9457 `ProblemDetail` handler skeleton.

#### Teammate 3 (Infrastructure, Frontend & Test Framework)
- **Files Created:**
  - `infra/docker-compose.yml`
  - `infra/mongo-init/init-replica.sh`
  - `frontend/package.json`, `frontend/vite.config.ts`
  - `frontend/src/services/auth.ts`, `frontend/src/stores/authStore.ts`
  - `frontend/src/services/api.ts`
- **Architectural Integrations:**
  - Configured single-node MongoDB 7 replica set (`rs0`) with automated initiation.
  - Initialized Vue 3 SPA with Pinia in-memory token store (zero `localStorage` usage).
  - Scaffolded test runner configurations (JUnit 5 + MockMvc + Playwright).

---

### 2. Test Suite Statistics (Phase 1)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| **Unit & Contract Tests** | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **Security Baseline Tests** | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **Infrastructure Sanity Tests**| 0 | 0 | 0 | 0 | 0 | 0.0s |
| **TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

---

### 3. Bug Fix & Failed Test Resolution Log (Phase 1)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| *None logged yet* | - | - | - | - |

---

### 4. Technical Debt, Mocked & Stubbed Elements Ledger (Phase 1)

| Debt ID | Component | Mock / Stub / Debt Description | Justification / Phase 1 Scope | Target Elimination Phase | Status |
|---|---|---|---|---|---|
| TD-001 | Security | In-memory mock JWKS decoder for offline tests | Allows testing without live Keycloak container | Phase 3 (Security Tests) | Active |
| TD-002 | Product Module | In-memory product seed data in initializer | Allows testing cart additions before admin UI ready | Phase 2 (Core Mongo CRUD) | Active |
| TD-003 | Frontend | Temporary mock login bypass switch in dev mode | Unblocks UI building before Keycloak container starts | Phase 2 (OIDC Integration) | Active |

---

## Phase 2: Core Domain, Transactions & Frontend Views

### 1. Changes Done in Phase 2
*(To be detailed upon execution of Phase 2)*

### 2. Test Suite Statistics (Phase 2)
*(To be detailed upon execution of Phase 2)*

### 3. Bug Fix & Failed Test Resolution Log (Phase 2)
*(To be detailed upon execution of Phase 2)*

### 4. Technical Debt, Mocked & Stubbed Elements Ledger (Phase 2)
*(To be detailed upon execution of Phase 2)*

---

## Phase 3: Comprehensive Testing Suite & Concurrency

### 1. Changes Done in Phase 3
*(To be detailed upon execution of Phase 3)*

### 2. Test Suite Statistics (Phase 3)
*(To be detailed upon execution of Phase 3)*

### 3. Bug Fix & Failed Test Resolution Log (Phase 3)
*(To be detailed upon execution of Phase 3)*

### 4. Technical Debt, Mocked & Stubbed Elements Ledger (Phase 3)
*(To be detailed upon execution of Phase 3)*

---

## Phase 4: Observability, Smoke Path & Final Pass

### 1. Changes Done in Phase 4
*(To be detailed upon execution of Phase 4)*

### 2. Final Test Suite Statistics & Code Health
*(To be detailed upon execution of Phase 4)*

### 3. Final Bug Fix Log & Retrospective
*(To be detailed upon execution of Phase 4)*

### 4. Technical Debt & Mock Elimination Sign-off
*(Final pass certifying all mocks, stubs, and temporary shims from previous phases are eradicated)*

| Debt ID | Component | Original Debt Description | Verification of Complete Removal | Sign-off Date |
|---|---|---|---|---|
| TD-001 | Security | Mock JWKS decoder | Verified real Keycloak JWKS endpoint validation | Pending |
| TD-002 | Product Module | In-memory product seed | Verified persistent MongoDB collection backing | Pending |
| TD-003 | Frontend | Mock login bypass | Verified real OIDC Authorization Code + PKCE flow | Pending |
