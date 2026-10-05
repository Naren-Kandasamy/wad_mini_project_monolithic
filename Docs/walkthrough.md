# Cumulative Project Walkthrough & Verification Journal

## Living Document Purpose
This walkthrough is the authoritative, phase-by-phase operational journal for the **Shopping Cart Modular Monolith** development. It is structured to monitor **3 parallel development tracks** (one per teammate on isolated git branches), followed by the **joint integration and hardening pass**.

Every track entry captures:
1. **Changes Done in That Track/Phase** (Primary focus: files created, modified, architectural integrations completed).
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

## Master Track Progression Tracker

| Track / Stream | Owner & Scope | Branch | Status | Tests Run | Pass Rate | Unresolved Tech Debt |
|---|---|---|---|---|---|---|
| **Track 1** | Teammate 1: Core Domain & Tx Engine | `feature/track1-core-domain` | 🟡 Ready to Begin | 0 | - | 0 |
| **Track 2** | Teammate 2: Security & Platform API | `feature/track2-security-platform` | 🟡 Ready to Begin | 0 | - | 0 |
| **Track 3** | Teammate 3: Infra, Vue 3 & E2E Suite | `feature/track3-infra-frontend-tests` | 🟡 Ready to Begin | 0 | - | 0 |
| **Joint Phase 4**| All Teammates: Merge, Smoke & Hardening | `main` | ⚪ Pending | 0 | - | - |

---

## Track 1: Core Domain & Transaction Engine (Teammate 1)
**Branch:** `feature/track1-core-domain`  
**Reference Document:** [architecture-monolith-grp1-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp1-ready.md)

### 1. Changes Done in Track 1
*(Populated as Track 1 tasks are committed)*
- **Files Created/Modified:**
  - `backend/src/main/java/com/example/shoppingcart/product/**`
  - `backend/src/main/java/com/example/shoppingcart/cart/**`
  - `backend/src/main/java/com/example/shoppingcart/order/**`
  - `backend/src/main/java/com/example/shoppingcart/checkout/**`
- **Architectural Integrations:**
  - In-process typed contracts: `ProductApi`, `CartApi`, `OrderApi`
  - Optimistic locking via Cart `version` (aborts with 409 Conflict on version clash)
  - Single `@Transactional` MongoDB multi-document transaction demarcated by Checkout
  - Database unique constraint on `orders (userId, idempotencyKey)`

### 2. Test Suite Statistics (Track 1)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| Domain Unit Tests (Calculations, Snapshots) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Mongo Persistence & Rollback Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Idempotency DB Unique Index Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Bug Fix & Failed Test Resolution Log (Track 1)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| *None logged yet* | - | - | - | - |

### 4. Technical Debt & Mocked Elements Ledger (Track 1)

| Debt ID | Component | Mock / Stub / Debt Description | Target Elimination Phase | Status |
|---|---|---|---|---|
| TD-T1-01 | Product | In-memory product catalog seed for initial Cart testing | Joint Phase 4 | Active |

---

## Track 2: Security & Platform API (Teammate 2)
**Branch:** `feature/track2-security-platform`  
**Reference Document:** [architecture-monolith-grp2-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp2-ready.md)

### 1. Changes Done in Track 2
*(Populated as Track 2 tasks are committed)*
- **Files Created/Modified:**
  - `infra/keycloak/realm-export.json`
  - `backend/src/main/java/com/example/shoppingcart/security/**`
  - `backend/src/main/java/com/example/shoppingcart/shared/filter/CorrelationIdFilter.java`
  - `backend/src/main/java/com/example/shoppingcart/shared/error/GlobalExceptionHandler.java`
- **Architectural Integrations:**
  - Single Spring Security 6 Resource Server boundary with JWT validation
  - Audience mapper validation (`shopping-cart-api`) & custom `realm_access.roles` converter
  - Server-authoritative identity extraction from `Jwt.sub` (404 on foreign order access)
  - RFC 7807/9457 `ProblemDetail` responses and SLF4J MDC `X-Request-Id` correlation

### 2. Test Suite Statistics (Track 2)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| JWT Validation Tests (Signature, Exp, Aud) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Route RBAC Tests (Admin vs User) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| IDOR & Ownership Protection Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Bug Fix & Failed Test Resolution Log (Track 2)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| *None logged yet* | - | - | - | - |

### 4. Technical Debt & Mocked Elements Ledger (Track 2)

| Debt ID | Component | Mock / Stub / Debt Description | Target Elimination Phase | Status |
|---|---|---|---|---|
| TD-T2-01 | Security | Mock JWKS decoder used for isolated offline tests | Joint Phase 4 | Active |

---

## Track 3: Infrastructure, Vue 3 SPA & Automated E2E Suite (Teammate 3)
**Branch:** `feature/track3-infra-frontend-tests`  
**Reference Document:** [architecture-monolith-grp3-ready.md](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Docs/architecture-monolith-grp3-ready.md)

### 1. Changes Done in Track 3
*(Populated as Track 3 tasks are committed)*
- **Files Created/Modified:**
  - `infra/docker-compose.yml`
  - `frontend/**`
  - `e2e/**`
  - `backend/src/test/java/com/example/shoppingcart/architecture/**`
  - `backend/src/test/java/com/example/shoppingcart/concurrency/**`
- **Architectural Integrations:**
  - Single-node MongoDB 7 replica set (`rs0`) with automated initiation
  - Vue 3 SPA with Pinia in-memory token store (zero `localStorage` usage)
  - ArchUnit tests guarding package encapsulation and forbidden repository imports
  - Concurrency harness testing cart version clashes and idempotency races
  - Playwright golden path E2E test

### 2. Test Suite Statistics (Track 3)

| Test Suite Category | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| ArchUnit Boundary Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Concurrency & Race Condition Tests | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Playwright E2E Golden Path | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Bug Fix & Failed Test Resolution Log (Track 3)

| Failure ID | Component / Test Name | Observed Symptom | Root Cause Analysis | Corrective Fix Applied |
|---|---|---|---|---|
| *None logged yet* | - | - | - | - |

### 4. Technical Debt & Mocked Elements Ledger (Track 3)

| Debt ID | Component | Mock / Stub / Debt Description | Target Elimination Phase | Status |
|---|---|---|---|---|
| TD-T3-01 | Frontend | Mock login toggle switch during UI template prototyping | Joint Phase 4 | Active |

---

## Joint Phase 4: Integration, Hardening & Final Pass (All Teammates)
**Branch:** `main` (Merge of Tracks 1, 2, and 3)

### 1. Changes Done in Joint Phase 4
*(To be detailed upon merging all tracks)*

### 2. Consolidated Test Suite Statistics

| Master Suite | Tests Run | Passed | Failed | Warnings | Skipped | Total Duration |
|---|---|---|---|---|---|---|
| Unit & Persistence (Track 1) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Security Integration (Track 2) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Architecture & Concurrency (Track 3) | 0 | 0 | 0 | 0 | 0 | 0.0s |
| Playwright Golden Path E2E | 0 | 0 | 0 | 0 | 0 | 0.0s |
| **GRAND TOTAL** | **0** | **0** | **0** | **0** | **0** | **0.0s** |

### 3. Master Technical Debt & Mock Elimination Sign-off

| Debt ID | Originating Track | Original Debt Description | Verification of Complete Removal | Sign-off Date |
|---|---|---|---|---|
| TD-T1-01 | Track 1 | Mock product seed | Verified persistent MongoDB collection backing | Pending |
| TD-T2-01 | Track 2 | Mock JWKS decoder | Verified live Keycloak JWKS endpoint validation | Pending |
| TD-T3-01 | Track 3 | Mock login toggle switch | Verified real OIDC Authorization Code + PKCE flow | Pending |
