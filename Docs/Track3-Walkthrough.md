# Track 3: Detailed Testing & Production Readiness Walkthrough

## Overview

This document outlines the approach, structure, and files included for
**Track 3: Verification, Observability, Deployment, Reliability & Operational Architecture**
of the Shopping Cart Full-Stack Web Application.

**Owner:** Nitin H (Nightingales21) — PoC committed on `track3-testing-readiness`  
**Integrated into `main`:** via PR #3 after selective rebase onto the merged Track 1 + 2 codebase.

---

## Stack Used

- **Spring Boot 4.1.1** on Java 21 (aligned with Tracks 1 & 2)
- **MongoDB 7.0** single-node replica set `rs0` on port `27018`
- **Keycloak 24.0** (OIDC/JWT), realm `shopping-cart`
- **Testcontainers** (BOM-managed) for ephemeral MongoDB in CI
- **ArchUnit 1.3.0** for module-boundary enforcement

---

## What This Track Encompasses

### 1. CI/CD Pipeline (`.github/workflows/ci.yml`)

GitHub Actions workflow triggers on every push and PR to `main`.  
Runs `mvn -B verify -f backend/pom.xml` with JDK 21 Temurin.

### 2. Application Testing Strategy

| Test Class | Type | What It Verifies |
|---|---|---|
| `ArchitectureTest` | ArchUnit | 6 module-boundary rules (cross-module internal access forbidden, controllers cannot import repositories) |
| `CheckoutIntegrationTest` | Testcontainers | Ephemeral MongoDB spin-up; 401/403 enforcement; empty-cart 409 against a clean DB |
| `CheckoutTransactionIntegrationTest` | Local Mongo | Multi-document transaction commit & rollback |
| `CartVersionConflictIntegrationTest` | Local Mongo | Optimistic locking concurrency conflict → 409 |
| `IdempotencyRaceIntegrationTest` | Local Mongo | Same idempotency key from 2 threads → 1 order |
| `SecurityIntegrationTest` | MockMvc + Mock JWT | 12 endpoint security tests |

### 3. Observability & API Contracts

- **`logback-spring.xml`** — structured log pattern includes `requestId` MDC key from `CorrelationIdFilter` on every line.
- **`GlobalExceptionHandler`** — RFC 7807 `ProblemDetail` (implemented in Track 2, confirmed working).
- **Actuator** — `/actuator/health` exposed (no auth required) via `application.yml`.

### 4. Infrastructure & Deployment

- **`infra/docker-compose.yml`** — MongoDB 7.0 replica set on port 27018 + Keycloak 24 on port 8180 with realm auto-import.
- **`Dockerfile`** — Multi-stage build (JDK 21 Alpine builder → JRE 21 Alpine runtime, non-root user `spring`).

### 5. Tech Debt Resolved by Track 3 Integration

| Debt ID | Status | Resolution |
|---|---|---|
| **TD-T1-04** | ✅ RESOLVED | `infra/docker-compose.yml` exposes MongoDB on port `27018` (matching test config — no URI change needed) |
| **TD-T1-06** | ✅ RESOLVED | `ProductDataSeeder` inserts Keyboard, Mouse, Monitor on startup under `dev`/`docker` profiles |
| **TD-T1-05** | Pending Frontend | No Vue 3 SPA was built in the project timeline. Server-side UUID fallback in `CheckoutController:31` is safe for demo; must be replaced with client-generated UUID when the frontend is built |

---

## File Structure (Track 3 Contributions)

```text
/
├── .github/workflows/ci.yml              ← CI pipeline (build + test)
├── Dockerfile                             ← Multi-stage Docker build
├── infra/
│   ├── docker-compose.yml                ← MongoDB + Keycloak local env
│   └── keycloak/realm-export.json        ← Auto-imported Keycloak realm
└── backend/
    ├── pom.xml                            ← + Testcontainers, ArchUnit deps
    ├── src/main/java/com/example/shoppingcart/
    │   └── product/seeder/
    │       └── ProductDataSeeder.java     ← TD-T1-06 fix
    ├── src/main/resources/
    │   └── logback-spring.xml            ← Structured logging with requestId MDC
    └── src/test/java/com/example/shoppingcart/
        ├── ArchitectureTest.java          ← ArchUnit (6 rules)
        └── CheckoutIntegrationTest.java   ← Testcontainers smoke test
```

---

## Next Steps (Post Track 3 Merge)

1. **Vue 3 Frontend (TD-T1-05):** Build `frontend/` directory with Vite + Pinia + Keycloak JS. Generate UUID `Idempotency-Key` header on every checkout button click.
2. **E2E Playwright Tests:** Implement golden-path spec: Login → Catalog → Add to Cart → Checkout → Confirm order → Cart empty.
3. **Prometheus / Grafana (optional):** Add to `infra/docker-compose.yml` for metrics visualization.
