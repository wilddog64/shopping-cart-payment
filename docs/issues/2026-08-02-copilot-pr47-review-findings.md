# Copilot PR #47 review findings

**Date:** 2026-08-02
**PR:** #47 — `chore(payment): enable Stripe gateway (test mode) via configmap`
**Reviewer:** copilot-pull-request-reviewer[bot]

PR #47 flips `stripe.enabled: "false"` → `"true"` in `k8s/base/configmap.yaml` (the live-enablement
step). Copilot raised 5 comments — **all against the pre-merge state**, when Phase B (the real Stripe
gateway) had not yet reached `main`. Every finding is **resolved by state**: Phase B is now merged to
`main` (`23da153`) and merged into `feat/stripe-live`, so the code the findings referenced now exists.

---

## Findings 1–5 — resolved by the Phase A–F merge (no code/doc change needed)

| # | Comment | File:line | Why it no longer applies |
|---|---------|-----------|--------------------------|
| 1 | "gateway becomes enabled … `ProcessPayment`/`ProcessRefund` return `not_implemented`" | `k8s/base/configmap.yaml:19` | `StripeGateway.ProcessPayment` now makes a **real** `stripeclient.New(...).PaymentIntents.New(...)` charge (`go/internal/gateway/mock.go`). Charges are live; only **refund** is still deferred (PR2), and refunds are not part of the checkout flow. |
| 2 | "reads as if the gateway is usable" | `memory-bank/progress.md:5` | Accurate now — the gateway is usable for charges once the key is present. |
| 3 | "'Stripe live … enablement' is misleading" | `memory-bank/activeContext.md:30` | Same — the real charge path is on `main`. |
| 4 | "changelog suggests gateway enabled in test mode but Stripe returns `not_implemented`" | `CHANGELOG.md:24` | The flip entry now sits alongside the Phase B `Added` entry documenting the real charge implementation. Self-consistent. |
| 5 | "`go/cmd/server/main.go:47` snippet does not match repo (no API key arg)" | `docs/plans/enable-stripe-live.md:15` | `main.go:47` now calls `gateway.NewStripeGateway(cfg.StripeEnabled && cfg.StripeAPIKey != "", cfg.StripeAPIKey)` — the doc snippet matches exactly. |

**Root cause:** the enablement PR was opened before the implementation PRs (A–F) merged, so Copilot
correctly flagged that the docs described code not yet on `main`. The chosen fix was ordering —
**merge A–F first, then enablement** — which this resolves rather than a doc rewrite.

**Remaining true limitation (not a checkout gate):** Stripe **refunds** still return
`not_implemented` (`mock.go`, "deferred to PR2"). Charges (the checkout path) are fully implemented.

---

## Test plan status

- [x] `feat/stripe-live` conflict-free with `main` after merge (`a304e87`)
- [x] `stripe.enabled: "true"` intact; `payment.gateway.default` still `mock`
- [x] Copilot threads replied + resolved (resolved-by-state)
