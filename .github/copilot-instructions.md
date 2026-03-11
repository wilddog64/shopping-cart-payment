# Copilot Instructions — Payment Service

## Service Overview

Java 21 / Spring Boot 3.2 PCI DSS compliant payment processing microservice.
PostgreSQL, RabbitMQ, Stripe/PayPal/Mock gateways, AES-256-GCM encryption.

---

## Architecture Guardrails

### Layer Boundaries — Never Cross These
- **Controller**: HTTP only — no payment processing logic, no gateway calls
- **Service**: payment orchestration — no HTTP concerns, no direct gateway calls (use router)
- **Gateway layer**: one class per provider (Stripe, PayPal, Mock) — isolated behind `PaymentGateway` interface
- **PaymentGatewayRouter**: provider selection only — no business logic
- A controller must never call a gateway directly. Always through PaymentService → PaymentGatewayRouter.

### Idempotency — Non-Negotiable
- Every payment request must be checked for idempotency before processing
- Duplicate requests (same idempotency key) must return the original result, never double-charge
- Never remove or weaken the idempotency check in `PaymentService`

### Gateway Isolation
- Each gateway implementation must be fully isolated — Stripe code never references PayPal classes
- Gateway selection is the router's responsibility — never hardcode a provider in the service layer
- MockGateway is test-only — it must never be reachable in a production environment without explicit config

---

## PCI DSS Rules (treat violations as critical security bugs)

### Card Data
- **Never store full card numbers (PAN)** — only tokenized payment methods
- **Never store CVV** — not even temporarily in a DB column, log, or variable beyond the gateway call
- Only store what the gateway returns: token, last4, expiry, brand
- `PciDataMasker` must be applied to all log output — never log raw card data

### Encryption
- All sensitive payment method data at rest must use `EncryptionService` (AES-256-GCM)
- Encryption keys come from Vault (`secret/data/payment/encryption`) — never hardcode
- Never implement your own encryption — always use the existing `EncryptionService`

### Access Control
- `PAYMENT_USER` role: process payments, view own payments
- `PAYMENT_ADMIN` role: issue refunds, view all payments
- Never elevate `PAYMENT_USER` to perform admin operations
- Service-to-service calls (from order service) must use JWT — never bypass auth for internal callers

### Secrets (OWASP A02)
- Stripe API keys → Vault `secret/data/payment/stripe`
- PayPal credentials → Vault `secret/data/payment/paypal`
- DB credentials → Vault dynamic secrets (`database/creds/payment-readwrite`)
- Never hardcode any of the above — not even in tests (use MockGateway)
- Never log gateway API keys, even partially

### Network Isolation
- Ingress: only from `shopping-cart-apps` namespace (order service)
- Egress to payment gateways (Stripe, PayPal APIs) only
- Egress to `shopping-cart-data` (PostgreSQL, RabbitMQ) only
- Never open ingress from the public internet directly to this service

---

## Security Rules (OWASP)

### Injection (OWASP A03)
- Never build SQL by string concatenation — Spring Data query methods or `@Query` with named params only
- Validate all inbound payment requests before processing

### Cryptographic Failures (OWASP A02)
- Never disable TLS on gateway connections
- Never use MD5 or SHA-1 for any security purpose

### Audit Logging
- Every payment state change must be logged to the `Transaction` table
- Refunds must record the authorizing admin user ID
- Never delete or truncate transaction logs

---

## Code Quality Rules

### Testing
- Unit tests required for all new payment and refund logic
- Integration tests use Testcontainers — never mock PostgreSQL in integration tests
- MockGateway test cards are the only acceptable way to simulate gateway responses in tests
- Never delete or weaken existing tests
- Run `./mvnw test` before every commit; must pass clean

### Code Style
- Use records for immutable DTOs
- Constructor injection only — never `@Autowired` field injection
- `@Transactional` on all service methods that modify state
- Never use `System.out.println` in production code
- PCI data masking must be applied before any log statement involving payment entities

---

## Completion Report Requirements

Before marking any task complete, the agent must provide:
- `./mvnw test` output (must be clean)
- Confirmation that no PAN, CVV, or API key appears in any changed file
- Confirmation that `PciDataMasker` is applied to all new log output involving payment data
- Confirmation that no test was deleted or weakened
- List of exact files modified

---

## What NOT To Do

- Do not add new payment gateway providers without a spec reviewed by Claude
- Do not add direct REST calls between services — use RabbitMQ events
- Do not change the `PaymentStatus` enum without a DB migration plan
- Do not disable idempotency checks under any circumstances
- Do not store CVV or full PAN under any circumstances, including in test fixtures
