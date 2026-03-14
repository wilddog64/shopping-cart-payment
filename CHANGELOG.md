# Changelog

## [0.1.0] - 2026-03-14

### Added
- Payment processing with idempotency (PENDING→PROCESSING→COMPLETED/FAILED)
- Refund processing: full and partial refunds
- Multi-gateway support: MockGateway, StripeGateway, PayPalGateway
- AES-256-GCM encryption for sensitive data (explicit UTF-8 charset)
- PCI DSS log masking
- OAuth2/JWT authentication via Keycloak
- Rate limiting (Bucket4j)
- Flyway database migrations (V1 schema + V2 billing_email)
- Testcontainers integration tests
- Dockerfile (multi-stage, JRE Alpine, non-root user)
- Kubernetes manifests with NetworkPolicy (PCI DSS isolation)
- GitHub Actions CI: Checkstyle + SpotBugs gate + build/test + integration tests + ghcr.io push
- Branch protection (1 required review + CI status check)

### Fixed
- EncryptionService: explicit StandardCharsets.UTF_8 in encrypt/decrypt (was using default charset)
