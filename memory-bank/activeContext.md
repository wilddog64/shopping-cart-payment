# Active Context: Payment Service

## Current Status (2026-03-14)

CI green. PR #1 merged to main. Branch protection active.

## What's Implemented

- Payment processing with idempotency, PENDING→PROCESSING→COMPLETED/FAILED state machine
- Refund processing: full/partial refunds, RefundService with 5-arg `processRefund(UUID, BigDecimal, String, String, String)`
- Gateways: MockGateway, StripeGateway, PayPalGateway
- Security: AES-256-GCM encryption, PCI log masking, OAuth2/JWT (Keycloak), Bucket4j rate limiting
- Flyway migration V1__init_schema.sql, 4 tables
- Integration tests: Testcontainers, PaymentServiceIntegrationTest, RefundServiceIntegrationTest, PaymentControllerIntegrationTest
- Dockerfile multi-stage, k8s/base manifests, GitHub Actions ci.yaml

## CI History

- **fix/ci-stabilization PR #1** — merged 2026-03-14. Fixed: mvnw multiModuleProjectDirectory, testcontainers dep, exception stubs, processRefund signature, flyway version, PayPal SDK version, PACKAGES_TOKEN auth.
- **Branch protection** — 1 review + CI required, enforce_admins: false

## Active Task

- **P4 linter** — Checkstyle + SpotBugs. Spec: `wilddog64/shopping-cart-infra/docs/plans/p4-linter-payment.md`. Branch: `feature/p4-linter`. Not started.

## Key Notes

- Local Java 25 vs pom Java 21 — do NOT run `mvn verify` locally. Use CI.
- `ENCRYPTION_KEY` required for EncryptionService — set `ENCRYPTION_ENABLED=false` for local dev without Vault
- `PAYMENT_GATEWAY_DEFAULT=mock` — no real gateway credentials needed for tests
