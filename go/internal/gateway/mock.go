package gateway

import (
	"math/rand"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/stripe/stripe-go/v79"
	stripeintent "github.com/stripe/stripe-go/v79/paymentintent"
)

const (
	mockDeclineToken           = "tok_mock_decline"
	mockInsufficientFundsToken = "tok_mock_insufficient_funds"
)

type MockGateway struct {
	enabled     bool
	delayMs     int
	failureRate float64
	mu          sync.Mutex
	tokens      map[string]string
}

func NewMockGateway(enabled bool, delayMs int, failureRate float64) *MockGateway {
	return &MockGateway{
		enabled:     enabled,
		delayMs:     delayMs,
		failureRate: failureRate,
		tokens:      map[string]string{},
	}
}

func (g *MockGateway) GetName() string             { return "mock" }
func (g *MockGateway) IsEnabled() bool             { return g.enabled }
func (g *MockGateway) SupportsRecurring() bool     { return true }
func (g *MockGateway) SupportsPartialRefund() bool { return true }

func (g *MockGateway) ProcessPayment(request PaymentRequest) PaymentResult {
	g.simulateDelay()
	if request.CardNumber != "" {
		switch request.CardNumber {
		case mockDeclineToken:
			return PaymentResultFailure("card_declined", "Your card was declined")
		case mockInsufficientFundsToken:
			return PaymentResultFailure("insufficient_funds", "Insufficient funds")
		}
	}
	if g.shouldFail() {
		return PaymentResultFailure("processing_error", "Random mock failure for testing")
	}
	last4 := "4242"
	if len(request.CardNumber) >= 4 {
		last4 = request.CardNumber[len(request.CardNumber)-4:]
	}
	return PaymentResult{
		Success:         true,
		TransactionID:   "mock_txn_" + shortID(),
		PaymentIntentID: "mock_pi_" + shortID(),
		Status:          "completed",
		CardLast4:       last4,
		CardBrand:       "visa",
		RawResponse:     "",
	}
}

func (g *MockGateway) ProcessRefund(request RefundRequest) RefundResult {
	g.simulateDelay()
	if g.shouldFail() {
		return RefundResultFailure("refund_failed", "Random mock failure for testing")
	}
	return RefundResult{
		Success:     true,
		RefundID:    "mock_re_" + shortID(),
		Status:      "completed",
		RawResponse: "",
	}
}

func (g *MockGateway) Tokenize(request TokenizeRequest) TokenizeResult {
	g.simulateDelay()
	last4 := "4242"
	if len(request.CardNumber) >= 4 {
		last4 = request.CardNumber[len(request.CardNumber)-4:]
	}
	token := "mock_tok_" + shortID()
	g.mu.Lock()
	g.tokens[token] = request.CustomerID
	g.mu.Unlock()
	return TokenizeResult{
		Success:  true,
		Token:    token,
		Last4:    last4,
		Brand:    "visa",
		ExpMonth: request.CardExpMonth,
		ExpYear:  request.CardExpYear,
	}
}

func (g *MockGateway) DeleteToken(token string) bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	_, ok := g.tokens[token]
	if ok {
		delete(g.tokens, token)
	}
	return ok
}

func (g *MockGateway) simulateDelay() {
	if g.delayMs > 0 {
		time.Sleep(time.Duration(g.delayMs) * time.Millisecond)
	}
}

func (g *MockGateway) shouldFail() bool {
	return g.failureRate > 0 && rand.Float64() < g.failureRate
}

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
		return PaymentResultFailure("payment_"+string(pi.Status), "Stripe payment not completed: "+string(pi.Status))
	}

	last4, brand := "", ""
	if pi.LatestCharge != nil && pi.LatestCharge.PaymentMethodDetails != nil && pi.LatestCharge.PaymentMethodDetails.Card != nil {
		last4 = pi.LatestCharge.PaymentMethodDetails.Card.Last4
		brand = string(pi.LatestCharge.PaymentMethodDetails.Card.Brand)
	}
	return PaymentResult{Success: true, TransactionID: pi.ID, PaymentIntentID: pi.ID, Status: "completed", CardLast4: last4, CardBrand: brand}
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
	return TokenizeResultFailure("not_supported", "Use Stripe Elements to create a PaymentMethod client-side")
}

func (g *StripeGateway) DeleteToken(token string) bool { return g.IsEnabled() }

type PayPalGateway struct {
	enabled bool
}

func NewPayPalGateway(enabled bool) *PayPalGateway   { return &PayPalGateway{enabled: enabled} }
func (g *PayPalGateway) GetName() string             { return "paypal" }
func (g *PayPalGateway) IsEnabled() bool             { return g.enabled }
func (g *PayPalGateway) SupportsRecurring() bool     { return true }
func (g *PayPalGateway) SupportsPartialRefund() bool { return true }

func (g *PayPalGateway) ProcessPayment(request PaymentRequest) PaymentResult {
	if !g.IsEnabled() {
		return PaymentResultFailure("gateway_disabled", "PayPal gateway is not enabled")
	}
	return PaymentResultFailure("not_implemented", "PayPal gateway is not implemented yet (deferred to PR2)")
}

func (g *PayPalGateway) ProcessRefund(request RefundRequest) RefundResult {
	if !g.IsEnabled() {
		return RefundResultFailure("gateway_disabled", "PayPal gateway is not enabled")
	}
	return RefundResultFailure("not_implemented", "PayPal gateway is not implemented yet (deferred to PR2)")
}

func (g *PayPalGateway) Tokenize(request TokenizeRequest) TokenizeResult {
	if !g.IsEnabled() {
		return TokenizeResultFailure("gateway_disabled", "PayPal gateway is not enabled")
	}
	return TokenizeResult{Success: true, Token: "paypal_vault_" + uuid.NewString()[:16], Brand: "paypal"}
}

func (g *PayPalGateway) DeleteToken(token string) bool { return g.IsEnabled() }

func PaymentResultFailure(code, message string) PaymentResult {
	return PaymentResult{Success: false, Status: "failed", ErrorCode: code, ErrorMessage: message}
}

func RefundResultFailure(code, message string) RefundResult {
	return RefundResult{Success: false, Status: "failed", ErrorCode: code, ErrorMessage: message}
}

func TokenizeResultFailure(code, message string) TokenizeResult {
	return TokenizeResult{Success: false, ErrorCode: code, ErrorMessage: message}
}

func shortID() string {
	return strings.ReplaceAll(uuid.NewString(), "-", "")[:16]
}
