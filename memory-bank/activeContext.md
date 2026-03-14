# Active Context: Payment Service

## Current Status

**Essentially complete.** According to the CLAUDE.md implementation checklist, all core features, infrastructure, testing, and CI/CD are done. The only explicitly pending item is load/performance testing.

## What's Fully Implemented

### Business Logic
- Payment processing with idempotency (checks idempotency key and existing order payment before processing)
- Refund processing: full and partial refunds with validation (refund amount <= original payment amount)
- PaymentService: PENDING → PROCESSING → COMPLETED/FAILED state transitions
- RefundService: refund creation, gateway dispatch, status update
- PaymentGatewayRouter: selects gateway by name, falls back to configured default

### Gateways
- MockGateway: test card support (success/declined/insufficient funds), configurable delay and failure rate
- StripeGateway: Stripe PaymentIntent API integration
- PayPalGateway: PayPal Checkout SDK integration

### Security
- EncryptionService: AES-256-GCM encrypt/decrypt for PCI-sensitive fields
- PciDataMasker: log sanitization to strip card data
- OAuth2 Resource Server: JWT validation via Keycloak JWKS
- Role-based access: PAYMENT_USER / PAYMENT_ADMIN / PLATFORM_ADMIN
- Rate limiting: Bucket4j per-IP

### Database
- Flyway migration V1__init_schema.sql: all 4 tables with proper indexes
- Including unique index on `idempotency_key` in payments table
- Comments on PCI-sensitive columns

### Tests
- Unit: PaymentServiceTest, RefundServiceTest, PaymentControllerTest, MockGatewayTest, EncryptionServiceTest, PaymentGatewayRouterTest
- Integration: BaseIntegrationTest (Testcontainers), PaymentServiceIntegrationTest, RefundServiceIntegrationTest, PaymentControllerIntegrationTest
- Test config files: application-test.yml, application-integration-test.yml

### Infrastructure
- Dockerfile: multi-stage (maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine), non-root user
- .dockerignore present
- Makefile with extensive targets
- k8s/base/: deployment, service (8084), configmap, serviceaccount, networkpolicy, hpa (2-10), kustomization
- GitHub Actions: ci.yaml, pr-validation.yaml, release.yaml

## Known Pending Items

- [ ] **Load/performance testing** — explicitly called out as pending in CLAUDE.md
- [ ] Optional future: contract tests (Pact), chaos engineering tests, webhook handling for async payment updates

## CI Blocker — OPEN (2026-03-14)

**Branch:** `fix/ci-stabilization` — PR #1 open
**Failing step:** `Build and Test` → `Build with Maven` — compile error in integration tests
**Latest failing run:** `23080076005`

**Current errors:**
- `RefundServiceIntegrationTest.java` — `processRefund` called with 4 args; actual signature requires 5: `(UUID, BigDecimal, String, String, String)`. Affects ~14 call sites.
- `RefundServiceIntegrationTest.java:[316]` — `getRefundsByPaymentId(UUID)` does not exist on `RefundService`.

**Fixed so far (on this PR):**
- `testcontainers-junit-jupiter` dependency added to pom.xml
- `com.shoppingcart.payment.exception` package created (stub exceptions)
- `PaymentControllerIntegrationTest` updated to use `ProcessPaymentRequest` DTO
- `flyway-database-postgresql` version pinned to 10.6.0
- PayPal SDK version updated to 2.0.0
- `PACKAGES_TOKEN` secret wired for cross-repo GitHub Packages auth
- `packages: read` permission added to CI workflow

**Next task (assigned to Codex — 2026-03-14):**
Fix all `processRefund` call sites in `RefundServiceIntegrationTest.java` to match the 5-arg signature. Fix or remove the `getRefundsByPaymentId` call. Wait for CI green before updating this memory-bank.

## Local Dev Warning — Java Version Mismatch

Local machine has **OpenJDK 25** but pom.xml targets **Java 21**. Running `mvn verify`
locally may hang or timeout due to Testcontainers + module-system issues on JVM 25.

**CI is NOT affected** — GitHub Actions pins `JAVA_VERSION: '21'`.

**Codex workaround:** skip local `mvn verify`. Push fixes → monitor CI instead.
Full details: `docs/issues/2026-03-14-local-java-version-mismatch.md`

## Key Configuration to Note

The `ENCRYPTION_KEY` environment variable must be set for `EncryptionService` to function. In Kubernetes, this comes from the `payment-encryption-secret` ExternalSecret backed by Vault path `secret/data/payment/encryption`.

In local development without Vault:
- Set `ENCRYPTION_ENABLED=false` to bypass encryption, or
- Provide a base64-encoded 32-byte key as `ENCRYPTION_KEY`

## Default Gateway in Development

`PAYMENT_GATEWAY_DEFAULT=mock` — no real payment gateway credentials needed for development or testing. The MockGateway has a configurable `failure-rate` (default 0.0 = always succeed) and `delay-ms` (default 500ms to simulate latency).

## Integration Points

- **Called by**: Order Service after order creation (POST /api/payments)
- **PostgreSQL**: `payments` database; K8s: `shopping-cart-data` namespace
- **RabbitMQ**: `rabbitmq-client` dependency present but no event publishing visible in current PaymentService implementation — may be future work or handled differently
- **Stripe**: `STRIPE_API_KEY` and `STRIPE_WEBHOOK_SECRET` from Vault (`secret/data/payment/stripe`)
- **PayPal**: `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` from Vault (`secret/data/payment/paypal`); mode `sandbox` or `live`
- **Vault**: DB creds from `database/creds/payment-readwrite`; gateway keys from `secret/data/payment/*`

## API Endpoints Active

| Endpoint | Method | Role Required | Status |
|---|---|---|---|
| POST /api/payments | POST | PAYMENT_USER | Live |
| GET /api/payments/{id} | GET | PAYMENT_USER | Live |
| GET /api/payments?orderId= | GET | PAYMENT_USER | Live |
| GET /api/payments?customerId= | GET | PAYMENT_USER | Live |
| POST /api/payments/{id}/refund | POST | PAYMENT_ADMIN | Live |
| GET /actuator/health | GET | Public | Live |
| GET /actuator/prometheus | GET | Public | Live |
