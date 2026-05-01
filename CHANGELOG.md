# Changelog

## [Unreleased]

### Fixed
- Bump `build-push-deploy.yml` reusable workflow SHA from `999f8d70` to `39c3072` — resolves `Unable to resolve action 'aquasecurity/trivy-action@0.30.0'` CI failure; image now pushable to GHCR

## [0.1.1] - 2026-03-20

### Changed
- Reduce deployment replicas from 2 to 1 for dev/test environment; delete HPA (`minReplicas: 2` was scaling pods back up on single-node cluster); will reintroduce in v1.1.0 EKS

### Fixed
- Add missing `payment-db-credentials` and `payment-encryption-secret` Kubernetes Secrets
  to `k8s/base/secret.yaml` — resolves `CreateContainerConfigError` on Ubuntu k3s cluster;
  `encryption-key` uses a valid Base64-encoded dev placeholder (replace via Vault/ESO in production)
- Include `secret.yaml` in `k8s/base/kustomization.yaml` so ArgoCD deploys the Secrets automatically

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
