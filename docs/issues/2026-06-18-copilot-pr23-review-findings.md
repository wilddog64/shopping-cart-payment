# Copilot Review Findings — PR #23 (Go rewrite PR1 hardening)

**PR:** https://github.com/wilddog64/shopping-cart-payment/pull/23
**Review:** Copilot `4524340362` — "⚠️ Not ready to approve" (28 findings), posted against
pre-hardening commit `736028dd`.
**Resolution commit:** `f39fba1` — `fix(payment): harden Go PR1 per Copilot review (gateways, crypto, config, tx)`.
All 23 open threads replied to and resolved 2026-06-18.

## Findings fixed in `f39fba1`

| # | File:line | Finding | Fix |
|---|-----------|---------|-----|
| 1 | config.go:64 | DB password default `changeme123` | default → `""` (explicit `DB_PASSWORD` required) |
| 2 | config.go:70 | RabbitMQ guest/guest defaults | defaults → `""` |
| 3 | config.go:76 | Stripe enabled-by-default + placeholder key/secret | `StripeEnabled` default `false`; key/secret → `""` |
| 4 | config.go:81 | PayPal enabled-by-default + placeholder creds | `PayPalEnabled` default `false`; creds → `""` |
| 5 | encryption.go:37 | random key generated when `ENCRYPTION_KEY` empty | fail fast (error) instead of silent random key |
| 6 | encryption.go:63 | Encrypt gates on `s.enabled` not readiness | gate on `s.IsEnabled()` |
| 7 | encryption.go:86 | Decrypt gates on `s.enabled` not readiness | gate on `s.IsEnabled()` |
| 8 | gateway.go:37 | silent fallback to `mock` for unknown gateway | removed; errors at use time |
| 9 | main.go:49 | MockGateway always constructed enabled | `NewMockGateway(cfg.MockGatewayEnabled, …)` |
| 10 | service.go:77 | multi-write payment flow without a DB transaction | wrapped create→update→audit→update in `RunInTx` |
| 11 | health.go:40 | readiness pings DB with `context.Background()` | use request context |
| 12 | store.go:458 | `scanPaymentRow` silently drops `payment_method_id` parse error | return wrapped error |
| 13 | middleware.go:45 | unbounded per-IP rate-limiter map | bounded map + lastSeen TTL (10m) sweep janitor (1m) |
| 14 | mock.go:136 | StripeGateway returns fake success | fail fast `not_implemented` (deferred to PR2) |
| 15 | mock.go:175 | PayPalGateway returns fake success | fail fast `not_implemented` (deferred to PR2) |
| 16 | handler.go:203 | amount scale not validated vs NUMERIC(19,4) | reject `Amount.Exponent() < -4` → `AMOUNT_SCALE_INVALID` |
| 17 | handler.go:213 | refund amount scale not validated | same scale check |
| 18 | config.go:110 | `sslmode=disable` hardcoded | configurable `DBSSLMode` (`DB_SSLMODE`), url-escaped |
| 19 | service.go:38 | idempotency pre-check race | unique-violation (SQLSTATE 23505) returns existing payment |
| 20–24 | service_test.go:26/85, integration_test.go:60, mock_test.go:18, encryption_test.go:21 | full PAN/CVV literals in tests | replaced with `tok_test_4242` / `test-cvc` |

## Findings deliberately DECLINED (contract preservation)

The Go service replaces a Java backend whose REST contract is the acceptance contract
(shared `shopping-cart-e2e-tests` Playwright suite). Two suggestions were declined because
they would break that contract:

- **handler.go:49 — "require an idempotency key (400 on missing)".** Declined. The Java
  contract treats the idempotency key as optional. Double-charge risk is instead closed at
  the data layer: `orderId` + a unique constraint on `payments.idempotency_key`, with the
  concurrent race handled in `f39fba1` (unique-violation returns the existing payment).
- **handler.go:59 — "return 201 for all states instead of 202".** Declined. The Java
  contract returns 201 for a COMPLETED payment and 202 for a non-terminal result; the e2e
  suite asserts this split. The 201/202 split (handler.go:56-59) is preserved deliberately.

## Process note

The work-repo issue log + README update belongs in the same handoff as the code fix
(`/create-pr` Phase 2), not a follow-up — fold it into every future PR-findings spec.
