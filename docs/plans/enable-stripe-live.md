# Enable live Stripe gateway — flip `stripe.enabled` to `"true"`

**Branch (this repo):** `feat/stripe-live` (create from `origin/main`)
**File:** `k8s/base/configmap.yaml`

---

## Problem

The payment service gates its real Stripe gateway on **both** conditions
(`go/cmd/server/main.go:47`):

```go
stripeGateway := gateway.NewStripeGateway(cfg.StripeEnabled && cfg.StripeAPIKey != "", cfg.StripeAPIKey)
```

`STRIPE_API_KEY` is now supplied via ESO/Vault (Kubernetes Secret
`payment-gateway-secrets`, key `stripe-api-key`, sourced from Vault `secret/payment/stripe`),
but `STRIPE_ENABLED` is still `"false"` in the configmap — so the gateway stays in mock mode.
Flip it to `"true"` to enable the live (Stripe **test mode**) gateway.

**Safe to land before the key exists:** if the Vault key is absent, `StripeAPIKey == ""` keeps
the gateway in mock mode regardless of this flag. Enabling it early is harmless.

---

## Fix

### Change 1 — `k8s/base/configmap.yaml`: enable Stripe

**Exact old block:**

```yaml
  # Payment Gateway Configuration
  payment.gateway.default: "mock"
  mock.gateway.enabled: "true"
  stripe.enabled: "false"
  paypal.enabled: "false"
```

**Exact new block:**

```yaml
  # Payment Gateway Configuration
  payment.gateway.default: "mock"
  mock.gateway.enabled: "true"
  stripe.enabled: "true"
  paypal.enabled: "false"
```

> Do NOT change `payment.gateway.default` — the orchestrator selects the `stripe` gateway
> explicitly per request; `default` stays `mock` so unrelated callers are unaffected.

---

## Files Changed

| File | Change |
|------|--------|
| `k8s/base/configmap.yaml` | `stripe.enabled: "false"` → `"true"` |

---

## Rules

- Exactly one line changes. No other key touched.
- No Go code, no deployment.yaml, no secret values in git.
- `kubectl kustomize k8s/base` still renders (no YAML break).

---

## Definition of Done

- [ ] `stripe.enabled` is `"true"`; no other configmap key changed
- [ ] `kubectl kustomize k8s/base` renders without error
- [ ] Committed and pushed to `feat/stripe-live`
- [ ] memory-bank updated with commit SHA and task status

**Commit message (exact):**
```
chore(payment): enable Stripe gateway (test mode) via configmap
```

---

## What NOT to Do

- Do NOT create a PR
- Do NOT skip pre-commit hooks (`--no-verify`)
- Do NOT modify any file other than `k8s/base/configmap.yaml`
- Do NOT put any Stripe key value in git — the key comes from Vault via ESO
- Do NOT commit to `main` — work on `feat/stripe-live`
- Do NOT branch from anything but `origin/main`
