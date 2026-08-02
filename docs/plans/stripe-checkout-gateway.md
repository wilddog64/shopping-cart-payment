# Phase B — Payment service Stripe test-mode gateway

**Repo:** `shopping-cart-payment`  **Branch:** `feat/stripe-checkout-gateway`
**Module:** `github.com/wilddog64/shopping-cart-payment/go` (code under `go/`)
**Design:** `shopping-cart-frontend/docs/plans/stripe-checkout-orchestration-design.md`

---

## Objective

The `StripeGateway` is currently a stub — `go/internal/gateway/mock.go` returns `"Stripe gateway is not implemented yet (deferred to PR2)"`. Implement a real Stripe test-mode charge: given a PaymentMethod ID (already delivered to the gateway as `PaymentRequest.PaymentMethodToken` — see `service.go:80`), create and confirm a Stripe PaymentIntent and map the result. Card details never touch this code path — only the PaymentMethod token is used.

The service already selects the gateway by `req.Gateway` and passes an idempotency key, so no service-layer change is needed beyond the constructor signature.

---

## Before You Start

- `git checkout -b feat/stripe-checkout-gateway origin/main`
- Read `go/internal/gateway/mock.go` (the `StripeGateway` block), `go/internal/gateway/dto.go` (`PaymentRequest`/`PaymentResult`), `go/internal/payment/service.go` (mapping), `go/internal/config/config.go` (`STRIPE_ENABLED`/`STRIPE_API_KEY` already exist), `go/cmd/server/main.go:47`.

---

## Change 1 — dependency

```bash
cd go && go get github.com/stripe/stripe-go/v79@latest && go mod tidy
```

---

## Change 2 — `go/internal/gateway/mock.go`: real `StripeGateway`

**Add the two Stripe imports** to mock.go's import block.

**Old:**
```go
import (
	"math/rand"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
)
```
**New:**
```go
import (
	"math/rand"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/stripe/stripe-go/v79"
	stripeintent "github.com/stripe/stripe-go/v79/paymentintent"
)
```

**Replace the entire `StripeGateway` block.**

**Old:**
```go
type StripeGateway struct {
	enabled bool
}

func NewStripeGateway(enabled bool) *StripeGateway   { return &StripeGateway{enabled: enabled} }
func (g *StripeGateway) GetName() string             { return "stripe" }
func (g *StripeGateway) IsEnabled() bool             { return g.enabled }
func (g *StripeGateway) SupportsRecurring() bool     { return false }
func (g *StripeGateway) SupportsPartialRefund() bool { return true }

func (g *StripeGateway) ProcessPayment(request PaymentRequest) PaymentResult {
	if !g.IsEnabled() {
		return PaymentResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	return PaymentResultFailure("not_implemented", "Stripe gateway is not implemented yet (deferred to PR2)")
}

func (g *StripeGateway) ProcessRefund(request RefundRequest) RefundResult {
	if !g.IsEnabled() {
		return RefundResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	return RefundResultFailure("not_implemented", "Stripe gateway is not implemented yet (deferred to PR2)")
}

func (g *StripeGateway) Tokenize(request TokenizeRequest) TokenizeResult {
	if !g.IsEnabled() {
		return TokenizeResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	return TokenizeResult{Success: true, Token: "stripe_pm_" + uuid.NewString()[:16], Last4: last4(request.CardNumber), Brand: "visa", ExpMonth: request.CardExpMonth, ExpYear: request.CardExpYear}
}

func (g *StripeGateway) DeleteToken(token string) bool { return g.IsEnabled() }
```

**New:**
```go
type StripeGateway struct {
	enabled bool
	apiKey  string
}

func NewStripeGateway(enabled bool, apiKey string) *StripeGateway {
	return &StripeGateway{enabled: enabled, apiKey: apiKey}
}
func (g *StripeGateway) GetName() string             { return "stripe" }
func (g *StripeGateway) IsEnabled() bool             { return g.enabled }
func (g *StripeGateway) SupportsRecurring() bool     { return false }
func (g *StripeGateway) SupportsPartialRefund() bool { return true }

// ProcessPayment confirms a Stripe PaymentIntent using the PaymentMethod token
// created by Stripe Elements in the browser. Raw card data is never used here.
func (g *StripeGateway) ProcessPayment(request PaymentRequest) PaymentResult {
	if !g.IsEnabled() {
		return PaymentResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	if strings.TrimSpace(request.PaymentMethodToken) == "" {
		return PaymentResultFailure("missing_payment_method", "PaymentMethod ID is required")
	}
	stripe.Key = g.apiKey

	params := &stripe.PaymentIntentParams{
		Amount:        stripe.Int64(request.Amount.Shift(2).IntPart()),
		Currency:      stripe.String(strings.ToLower(request.Currency)),
		PaymentMethod: stripe.String(request.PaymentMethodToken),
		Confirm:       stripe.Bool(true),
	}
	// Card-only flow (Elements Card element): disallow redirect-based methods so
	// no return_url is required for server-side confirmation.
	params.AutomaticPaymentMethods = &stripe.PaymentIntentAutomaticPaymentMethodsParams{
		Enabled:        stripe.Bool(true),
		AllowRedirects: stripe.String("never"),
	}
	if strings.TrimSpace(request.Description) != "" {
		params.Description = stripe.String(request.Description)
	}
	if strings.TrimSpace(request.IdempotencyKey) != "" {
		params.SetIdempotencyKey(request.IdempotencyKey)
	}
	params.AddExpand("latest_charge")

	pi, err := stripeintent.New(params)
	if err != nil {
		if serr, ok := err.(*stripe.Error); ok {
			return PaymentResultFailure(string(serr.Code), serr.Msg)
		}
		return PaymentResultFailure("stripe_error", err.Error())
	}
	if pi.Status != stripe.PaymentIntentStatusSucceeded {
		return PaymentResultFailure("payment_"+string(pi.Status),
			"Stripe payment not completed: "+string(pi.Status))
	}

	last4, brand := "", ""
	if pi.LatestCharge != nil && pi.LatestCharge.PaymentMethodDetails != nil &&
		pi.LatestCharge.PaymentMethodDetails.Card != nil {
		last4 = pi.LatestCharge.PaymentMethodDetails.Card.Last4
		brand = string(pi.LatestCharge.PaymentMethodDetails.Card.Brand)
	}
	return PaymentResult{
		Success:         true,
		TransactionID:   pi.ID,
		PaymentIntentID: pi.ID,
		Status:          "completed",
		CardLast4:       last4,
		CardBrand:       brand,
	}
}

func (g *StripeGateway) ProcessRefund(request RefundRequest) RefundResult {
	if !g.IsEnabled() {
		return RefundResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	return RefundResultFailure("not_implemented", "Stripe refunds are not implemented yet")
}

func (g *StripeGateway) Tokenize(request TokenizeRequest) TokenizeResult {
	if !g.IsEnabled() {
		return TokenizeResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	// Tokenization happens client-side via Stripe Elements; server-side tokenize
	// is unsupported in the PaymentMethod flow.
	return TokenizeResultFailure("not_supported", "Use Stripe Elements to create a PaymentMethod client-side")
}

func (g *StripeGateway) DeleteToken(token string) bool { return g.IsEnabled() }
```

> If `uuid` or `last4` become unused in mock.go after this change, run `goimports`/`go mod tidy` and let `go vet`/`gofmt` guide you — do not remove usages elsewhere in the file. (MockGateway still uses `uuid` via `shortID`, so the import stays.)

---

## Change 3 — `go/cmd/server/main.go:47`: pass the API key

**Old:**
```go
	stripeGateway := gateway.NewStripeGateway(cfg.StripeEnabled && cfg.StripeAPIKey != "")
```
**New:**
```go
	stripeGateway := gateway.NewStripeGateway(cfg.StripeEnabled && cfg.StripeAPIKey != "", cfg.StripeAPIKey)
```

---

## Change 4 — new file `go/internal/gateway/stripe_test.go`

Hermetic unit tests for the guard paths (no network). The live Stripe-API charge is a documented smoke, not a unit test.

```go
package gateway

import (
	"testing"

	"github.com/shopspring/decimal"
)

func TestStripeGatewayDisabled(t *testing.T) {
	g := NewStripeGateway(false, "")
	res := g.ProcessPayment(PaymentRequest{PaymentMethodToken: "pm_card_visa", Currency: "usd", Amount: decimal.NewFromInt(10)})
	if res.Success || res.ErrorCode != "gateway_disabled" {
		t.Fatalf("expected gateway_disabled failure, got %+v", res)
	}
}

func TestStripeGatewayMissingPaymentMethod(t *testing.T) {
	g := NewStripeGateway(true, "sk_test_dummy")
	res := g.ProcessPayment(PaymentRequest{Currency: "usd", Amount: decimal.NewFromInt(10)})
	if res.Success || res.ErrorCode != "missing_payment_method" {
		t.Fatalf("expected missing_payment_method failure, got %+v", res)
	}
}
```

---

## Files Changed

| File | Change |
|------|--------|
| `go/internal/gateway/mock.go` | real `StripeGateway` (PaymentIntent confirm); constructor takes `apiKey` |
| `go/internal/gateway/stripe_test.go` | NEW — disabled + missing-PM guard tests |
| `go/cmd/server/main.go` | pass `cfg.StripeAPIKey` to `NewStripeGateway` |
| `go/go.mod`, `go/go.sum` | add `github.com/stripe/stripe-go/v79` |

---

## Rules

- `cd go && gofmt -l .` → no output
- `cd go && go vet ./...` → clean
- `cd go && go build ./...` → compiles
- `cd go && go test ./...` → all pass. **If any existing gateway test asserted the old `"not_implemented"` Stripe message, update that assertion** — the stub message is gone by design.
- No files touched outside the table above.

---

## Definition of Done

- [ ] Disabled and missing-PM guard paths covered by unit tests
- [ ] `go build ./...` and `go test ./...` pass under `go/`
- [ ] Mock gateway path unchanged (still the CI default)
- [ ] Committed and pushed to `feat/stripe-checkout-gateway`
- [ ] memory-bank updated with commit SHA and task status

**Live smoke (owner-run, not part of CI — document in the commit body or PR notes later):**
with `STRIPE_ENABLED=true` + a Stripe **test** secret key in `STRIPE_API_KEY`, a PaymentMethod from test card `4242 4242 4242 4242` → `PaymentResult{Success:true, Status:"completed"}`; a declined test card → `Success:false` with the Stripe decline code.

**Commit message (exact):**
```
feat(payment): implement Stripe test-mode gateway with PaymentMethod charge
```

---

## What NOT to Do

- Do NOT create a PR.
- Do NOT skip pre-commit hooks (`--no-verify`).
- Do NOT read or use the raw card fields (`CardNumber`/`CardCVC`/…) in the Stripe path — PaymentMethod token only.
- Do NOT hardcode any Stripe key; the key arrives via `STRIPE_API_KEY`.
- Do NOT change the mock gateway or router behavior.
- Do NOT commit to `main` — work on `feat/stripe-checkout-gateway`.
