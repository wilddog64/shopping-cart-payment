# Active Context: Payment Service

## Current Status (2026-03-14)

CI green. All PRs merged to main. Branch protection active.

## What's Implemented

- Payment processing with idempotency, PENDING→PROCESSING→COMPLETED/FAILED state machine
- Refund processing: full/partial refunds, RefundService 5-arg `processRefund`
- Gateways: MockGateway, StripeGateway, PayPalGateway
- Security: AES-256-GCM encryption (UTF-8 explicit), PCI log masking, OAuth2/JWT, Bucket4j rate limiting
- Flyway migrations V1 + V2, 4 tables
- GitHub Actions CI: Checkstyle + SpotBugs gate + build/test + integration tests + ghcr.io push

## CI History

- **fix/ci-stabilization PR #1** — merged 2026-03-14. Fixed: mvnw, testcontainers, processRefund signature, flyway, PayPal SDK, PACKAGES_TOKEN.
- **feature/p4-linter PR #2** — merged 2026-03-14. Added Checkstyle + SpotBugs; fixed DM_DEFAULT_ENCODING in EncryptionService; ST_WRITE_TO_STATIC suppressed via spotbugs-exclude.xml.
- **Branch protection** — 1 review + CI required, enforce_admins: false

## Active Task

- **CI fixes — Maven wrapper + GitHub Packages auth** — Dockerfile now installs Maven via apk and mounts `GH_TOKEN` secret for dependency resolution (commits `ad9bc86`, `377cdf4`). Latest run (`23175303688`) still fails to authenticate to GitHub Packages; ensure `PACKAGES_TOKEN` has read access to `wilddog64/rabbitmq-client-java` packages.

## Agent Instructions

Rules that apply to ALL agents working in this repo:

1. **CI only** — do NOT run `mvn` locally (local Java 25 vs pom Java 21).
2. **Memory-bank discipline** — do NOT update until CI shows `completed success`.
3. **SHA verification** — verify commit SHA before reporting.
4. **Do NOT merge PRs** — open the PR and stop.
5. **No unsolicited changes** — only touch files in the task spec.

## Key Notes

- Local Java 25 vs pom Java 21 — use CI only
- `ENCRYPTION_KEY` required — set `ENCRYPTION_ENABLED=false` for local dev
- `PAYMENT_GATEWAY_DEFAULT=mock` — no real gateway credentials needed for tests
