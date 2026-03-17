# Payment Service — Troubleshooting

## GitHub Packages / Maven Wrapper Errors
**Symptom:** `401 Unauthorized` when Maven downloads `rabbitmq-client-java` artifacts.
**Fix:** Ensure GitHub Actions workflow mounts `GH_TOKEN`/`PACKAGES_TOKEN` with read access to `wilddog64/rabbitmq-client-java`. See `docs/issues/2026-03-17-ci-maven-wrapper-fix.md` for the exact Dockerfile/script changes.

## Namespace Isolation vs shopping-cart-apps
**Symptom:** Deployments fail when targeting the shared `shopping-cart-apps` namespace.
**Fix:** Payment service must run in `shopping-cart-payment` to maintain PCI scope. Update manifests or `kubectl` commands accordingly, and ensure secrets exist in that namespace.

## Vault/ESO Credential Sync
**Symptom:** Gateway or DB credentials missing; pods crash with `Secret not found`.
**Fix:**
1. Verify `payment-gateway-secrets`, `payment-db-secret`, and `payment-encryption-secret` exist in `shopping-cart-payment`.
2. Check ExternalSecret status for sync errors.
3. Confirm Vault policies allow the service account to read the paths.

## Encryption Key Issues
**Symptom:** `IllegalBlockSizeException` or decryption failures on startup.
**Fix:**
- Ensure `PAYMENT_ENCRYPTION_KEY` is 32 bytes (Base64 for AES-256).
- Set `ENCRYPTION_ENABLED=false` for local testing if no key is available.

## Testcontainers / Local Maven Failures
**Symptom:** Local `mvn` commands fail due to Java version mismatch or missing Docker (Testcontainers).
**Fix:** Run tests in CI or align local Java to 21. Alternatively, disable integration profile locally: `mvn test -DskipITs`.

## Gateway Sandbox Errors
**Symptom:** Stripe/PayPal calls return auth errors in dev.
**Fix:** Confirm sandbox credentials in `payment-gateway-secrets`. Set gateway modes to `sandbox` in `application.yml` and restart pods after credential rotation.
