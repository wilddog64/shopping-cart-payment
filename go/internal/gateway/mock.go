package gateway

import (
	"math/rand"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
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
		case "4000000000000002":
			return PaymentResultFailure("card_declined", "Your card was declined")
		case "4000000000009995":
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
	return PaymentResult{
		Success:         true,
		TransactionID:   "stripe_txn_" + uuid.NewString()[:16],
		PaymentIntentID: "stripe_pi_" + uuid.NewString()[:16],
		Status:          "completed",
		RawResponse:     "",
	}
}

func (g *StripeGateway) ProcessRefund(request RefundRequest) RefundResult {
	if !g.IsEnabled() {
		return RefundResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	return RefundResult{Success: true, RefundID: "stripe_re_" + uuid.NewString()[:16], Status: "completed"}
}

func (g *StripeGateway) Tokenize(request TokenizeRequest) TokenizeResult {
	if !g.IsEnabled() {
		return TokenizeResultFailure("gateway_disabled", "Stripe gateway is not enabled")
	}
	return TokenizeResult{Success: true, Token: "stripe_pm_" + uuid.NewString()[:16], Last4: last4(request.CardNumber), Brand: "visa", ExpMonth: request.CardExpMonth, ExpYear: request.CardExpYear}
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
	return PaymentResult{
		Success:         true,
		TransactionID:   "paypal_txn_" + uuid.NewString()[:16],
		PaymentIntentID: "paypal_order_" + uuid.NewString()[:16],
		Status:          "completed",
		RawResponse:     "",
	}
}

func (g *PayPalGateway) ProcessRefund(request RefundRequest) RefundResult {
	if !g.IsEnabled() {
		return RefundResultFailure("gateway_disabled", "PayPal gateway is not enabled")
	}
	return RefundResult{Success: true, RefundID: "paypal_re_" + uuid.NewString()[:16], Status: "completed"}
}

func (g *PayPalGateway) Tokenize(request TokenizeRequest) TokenizeResult {
	if !g.IsEnabled() {
		return TokenizeResultFailure("gateway_disabled", "PayPal gateway is not enabled")
	}
	return TokenizeResult{Success: true, Token: "paypal_vault_" + uuid.NewString()[:16], Brand: "paypal"}
}

func (g *PayPalGateway) DeleteToken(token string) bool { return g.IsEnabled() }

func last4(cardNumber string) string {
	if len(cardNumber) >= 4 {
		return cardNumber[len(cardNumber)-4:]
	}
	return "4242"
}

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
