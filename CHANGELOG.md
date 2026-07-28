# Changelog

## [Unreleased]

### Added
- `.github/workflows/dependabot-automerge.yml`: auto-merge Dependabot minor/patch version updates and all security updates (any semver, via `alert-lookup`) with `gh pr merge --auto --squash` once required CI checks pass; **non-security** major bumps stay open for review (`dependabot/fetch-metadata` pinned to v2.3.0; `pull_request_target` scoped to `main`, job-level least-privilege permissions, gated on the PR author, no PR-head checkout)
- `.github/dependabot.yml`: Dependabot scheduled version updates for Maven dependencies, Docker base images, and GitHub Actions (weekly; minor/patch grouped, majors separate). Repository-level Dependabot security updates (immediate advisory-triggered PRs) are enabled separately as a repo setting — together they close the first-mile CVE gap so a flagged app dependency opens an update PR that CI builds into a clean image
- Go rewrite PR1 follow-ups: add unit and integration tests, CI gates, and a JSON-number amount shape for payment/refund responses under `go/`. The Go payment service remains side-by-side with the Java service.
- `.githooks/pre-push`: pre-push hook to block accidental direct pushes from feature branches to main; bypass with `ALLOW_MAIN_PUSH=1`

### Fixed
- Remove placeholder `secret.yaml` from kustomization — Secrets `payment-db-credentials` (created by ExternalSecret `postgres-payment-app`) and `payment-encryption-secret` (created by ExternalSecret `payment-encryption-secret`) are provisioned at runtime by ESO from Vault; they are not checked into git
- Update OAuth2 issuer URI from internal cluster domain to external Keycloak domain (`keycloak.3ai-talk.org`)
- Bump `build-push-deploy.yml` reusable workflow SHA from `999f8d70` to `39c3072` — resolves `Unable to resolve action 'aquasecurity/trivy-action@0.30.0'` CI failure; image now pushable to GHCR

### Changed
- `k8s/base/deployment.yaml`: add explicit `strategy: RollingUpdate` with `maxSurge: 0` / `maxUnavailable: 1` so rollouts complete on the single-node hostinger cluster instead of wedging with an unschedulable surge pod (previously relied on the Kubernetes default surge)

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
