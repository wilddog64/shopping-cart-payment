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
