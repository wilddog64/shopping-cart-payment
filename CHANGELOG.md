# Changelog

## [Unreleased]

### Added
- Stripe test-mode payment gateway in the Go service (Stripe checkout Phase B): `NewStripeGateway` now accepts the API key and creates a real Stripe charge from a client-supplied PaymentMethod token (`pm_…`), replacing the `not_implemented` stub. The gateway stays inert (mock behaviour) until `STRIPE_API_KEY` is provisioned via ESO/Vault, so the change is safe before the key exists. Spec: `docs/plans/` Phase B payment Stripe test-mode gateway.
- `.github/workflows/ci.yaml`: add a PR-only, no-push Docker image build check to catch base-image and JDK compatibility failures before merge
- `.github/workflows/dependabot-automerge.yml`: auto-merge Dependabot minor/patch version updates and all security updates (any semver, via `alert-lookup`) with `gh pr merge --auto --squash` once required CI checks pass; **non-security** major bumps stay open for review (`dependabot/fetch-metadata` pinned to v2.3.0; `pull_request_target` scoped to `main`, job-level least-privilege permissions, gated on the PR author, no PR-head checkout)
- `.github/dependabot.yml`: Dependabot scheduled version updates for Maven dependencies, Docker base images, and GitHub Actions (weekly; minor/patch grouped, majors separate). Repository-level Dependabot security updates (immediate advisory-triggered PRs) are enabled separately as a repo setting — together they close the first-mile CVE gap so a flagged app dependency opens an update PR that CI builds into a clean image
- Go rewrite PR1 follow-ups: add unit and integration tests, CI gates, and a JSON-number amount shape for payment/refund responses under `go/`. The Go payment service remains side-by-side with the Java service.
- `.githooks/pre-push`: pre-push hook to block accidental direct pushes from feature branches to main; bypass with `ALLOW_MAIN_PUSH=1`

### Fixed
- Make the GHCR pull secret ESO-owned and attach it to the payment-service ServiceAccount so GitOps pruning does not break image pulls
- `Dockerfile`: revert Java builder and runtime base images to Eclipse Temurin 21 because JDK 25 breaks Spring Boot 3.2.0-managed Lombok 1.18.30 annotation processing
- Remove placeholder `secret.yaml` from kustomization — Secrets `payment-db-credentials` (created by ExternalSecret `postgres-payment-app`) and `payment-encryption-secret` (created by ExternalSecret `payment-encryption-secret`) are provisioned at runtime by ESO from Vault; they are not checked into git
- Update OAuth2 issuer URI from internal cluster domain to external Keycloak domain (`keycloak.3ai-talk.org`)
- Bump `build-push-deploy.yml` reusable workflow SHA from `999f8d70` to `39c3072` — resolves `Unable to resolve action 'aquasecurity/trivy-action@0.30.0'` CI failure; image now pushable to GHCR
- `.github/workflows/ci.yaml`: drop the redundant inline `docker-build` job. It raced the reusable `publish` job by also pushing `latest` (plus a bare short-sha and `main` tag), so `latest` pointed at the inline build while the immutable `sha-<gitsha>` tag came from `publish` — leaving `latest` matching no `sha-*` by digest and blocking the Hub `app-cve-scan` promoter. The reusable `publish` job is now the single source of `latest`, co-tagged with `sha-<gitsha>`
- `.github/workflows/go-ci.yml`: bump `golangci-lint` from `v2.5.0` to `v2.7.2`. The `v2.5.0` binary is built with go1.23 and refuses to lint code targeting go1.25 (`the Go language version (go1.23) used to build golangci-lint is lower than the targeted Go version (1.25.0)`), failing the `go` check on every Go Dependabot PR (#37 pgx, #38 net); the `v2.7.2` binary is built with go1.25 and lints the module successfully
- `go/Dockerfile`: bump the build stage from `FROM golang:1.21 AS build` to `FROM golang:1.25 AS build` to match `go/go.mod`'s `go 1.25.0` directive (raised by the pgx #37 and x/net #38 bumps). With the default `GOTOOLCHAIN=local`, go1.21 refuses to build a module requiring go >= 1.25.0, so the `docker build` step in the `go` job failed on `main` once the golangci-lint failure that had masked it was fixed. The runtime stage (`gcr.io/distroless/static-debian12:nonroot`) is unchanged

### Changed
- `k8s/base/deployment.yaml`: add explicit `strategy: RollingUpdate` with `maxSurge: 0` / `maxUnavailable: 1` so rollouts complete on the single-node hostinger cluster instead of wedging with an unschedulable surge pod (previously relied on the Kubernetes default surge)
- `k8s/base/configmap.yaml`: enable the Stripe payment gateway in test mode (`stripe.enabled: "false"` → `"true"`). The gateway still gates on a non-empty `STRIPE_API_KEY` (`StripeEnabled && StripeAPIKey != ""`), so it stays in mock mode until the key is provisioned via ESO/Vault — flipping the flag is safe before the key exists. `payment.gateway.default` remains `mock`.

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
