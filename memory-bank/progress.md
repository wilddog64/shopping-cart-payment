# Progress: Payment Service

## What's Built

### Core Entities
- [x] `Payment` entity — full field set including idempotency_key, correlation_id, card_last4, card_brand
- [x] `Refund` entity — linked to Payment, partial refund support
- [x] `PaymentMethod` entity — tokenized, encrypted sensitive fields
- [x] `Transaction` entity — immutable audit log
- [x] `PaymentStatus` enum: PENDING, PROCESSING, COMPLETED, FAILED, REFUND_PENDING, REFUNDED, REFUND_FAILED
- [x] `RefundStatus` enum: PENDING, PROCESSING, COMPLETED, FAILED
- [x] `PaymentMethodType` enum
- [x] `TransactionType` enum: CHARGE, REFUND, TOKENIZE

### Repositories
- [x] `PaymentRepository` — with `findByIdempotencyKey`, `findByOrderId`, `findByCustomerId`
- [x] `RefundRepository`
- [x] `PaymentMethodRepository`
- [x] `TransactionRepository`

### Business Logic
- [x] `PaymentService` — processPayment (idempotent), getPayment, getPaymentByOrderId, getPaymentsByCustomer, updatePaymentStatus
- [x] `RefundService` — full and partial refunds with validation

### Gateway Layer
- [x] `PaymentGateway` interface
- [x] `PaymentGatewayRouter` — selection by name with default fallback
- [x] `PaymentRequest`, `PaymentResult`, `RefundRequest`, `RefundResult`, `TokenizeRequest`, `TokenizeResult` (gateway DTOs)
- [x] `MockGateway` — test cards, configurable delay/failure-rate
- [x] `StripeGateway` — Stripe PaymentIntent API
- [x] `PayPalGateway` — PayPal Checkout SDK

### Security
- [x] `EncryptionService` — AES-256-GCM encrypt/decrypt (Bouncy Castle)
- [x] `PciDataMasker` — log sanitization
- [x] OAuth2 Resource Server configuration (JWT, Keycloak JWKS)
- [x] Role-based authorization: PAYMENT_USER, PAYMENT_ADMIN, PLATFORM_ADMIN

### API Layer
- [x] `PaymentController` — all 5 endpoints with `@PreAuthorize` annotations
- [x] `ProcessPaymentRequest` DTO
- [x] `PaymentResponse` DTO
- [x] `RefundRequest` DTO

### Database
- [x] `V1__init_schema.sql` — all 4 tables, all indexes (including unique idempotency_key index), PCI compliance comments
- [x] Flyway configuration in application.yml
- [x] HikariCP connection pool configuration

### Tests
- [x] `PaymentServiceTest` — payment processing, idempotency, failure scenarios
- [x] `RefundServiceTest` — full/partial refunds, validation
- [x] `PaymentControllerTest` — API endpoints with mock security
- [x] `MockGatewayTest` — test cards, tokenization
- [x] `EncryptionServiceTest` — AES-256-GCM encrypt/decrypt
- [x] `PaymentGatewayRouterTest` — gateway routing logic
- [x] `BaseIntegrationTest` — Testcontainers PostgreSQL base class
- [x] `PaymentServiceIntegrationTest` — persistence, idempotency with real DB
- [x] `RefundServiceIntegrationTest` — refund flows with real DB
- [x] `PaymentControllerIntegrationTest` — full API testing with real DB
- [x] `application-test.yml` — unit test configuration
- [x] `application-integration-test.yml` — integration test configuration

### Infrastructure & Operations
- [x] `pom.xml` — complete with all dependencies, Maven wrapper
- [x] `application.yml` — full configuration with env var overrides and defaults
- [x] `Dockerfile` — multi-stage JRE Alpine, non-root user
- [x] `.dockerignore`
- [x] `mvnw` Maven wrapper script
- [x] `.mvn/wrapper/maven-wrapper.properties`
- [x] `Makefile` — build, test, coverage, lint, format, docker, db, CI targets
- [x] `k8s/base/deployment.yaml` — with security context, resource limits, liveness/readiness probes
- [x] `k8s/base/service.yaml` — ClusterIP port 8084
- [x] `k8s/base/configmap.yaml`
- [x] `k8s/base/serviceaccount.yaml`
- [x] `k8s/base/networkpolicy.yaml` — PCI DSS network isolation
- [x] `k8s/base/hpa.yaml` — 2–10 replicas
- [x] `k8s/base/kustomization.yaml`
- [x] `.github/workflows/ci.yaml`
- [x] CI workflow pin updated to `build-push-deploy.yml@999f8d7` (multi-arch) — 2026-03-17
- [x] `.github/workflows/pr-validation.yaml`
- [x] `.github/workflows/release.yaml`
- [x] `.gitignore`

### Documentation
- [x] `CLAUDE.md` — detailed AI guidance with implementation checklist
- [x] `README.md` — setup, API reference, configuration, testing guide
- [x] `docs/api/payments.md` — full API documentation with request/response schemas
- [x] `docs/testing/README.md` — testing guide

## What's Pending

- [ ] **Load/performance testing** — the only item explicitly marked pending in CLAUDE.md
- [ ] **Webhook handling** for async payment status updates from gateways (e.g., Stripe webhooks for 3DS)
- [ ] Contract tests (Pact)
- [ ] Chaos engineering tests

## Recent Work

- 2026-03-17 — Dockerfile switched to system Maven and BuildKit `GH_TOKEN` secret to download GitHub Packages dependencies (commits `ad9bc86`, `377cdf4`). CI run 23175303688 still in progress.

## Key Metrics

| Metric | Name | Labels |
|---|---|---|
| Payment processing latency | `payment_processing_seconds` | gateway, status |
| Payment count | `payment_total` | status |
| Refund count | `refund_total` | status |

## API Endpoints Summary

| Method | Path | Auth Role | Description |
|---|---|---|---|
| POST | `/api/payments` | PAYMENT_USER | Process payment |
| GET | `/api/payments/{id}` | PAYMENT_USER | Get payment by ID |
| GET | `/api/payments?orderId=` | PAYMENT_USER | Get payment by order |
| GET | `/api/payments?customerId=` | PAYMENT_USER | List customer payments |
| POST | `/api/payments/{id}/refund` | PAYMENT_ADMIN | Refund payment |
| GET | `/actuator/health` | Public | Health check |
| GET | `/actuator/prometheus` | Public | Prometheus metrics |
