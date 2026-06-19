package gateway

import (
	"testing"

	"github.com/shopspring/decimal"
)

func TestMockGatewaySuccessAndFailurePaths(t *testing.T) {
	t.Run("success path is deterministic at zero failure rate", func(t *testing.T) {
		gw := NewMockGateway(true, 0, 0)
		result := gw.ProcessPayment(PaymentRequest{
			OrderID:    "order-1",
			CustomerID: "customer-1",
			Amount:     decimal.RequireFromString("12.3400"),
			Currency:   "USD",
			CardNumber: "tok_test_4242",
		})
		if !result.Success {
			t.Fatalf("expected success result, got %+v", result)
		}
		if result.CardLast4 != "4242" {
			t.Fatalf("card last4 = %s, want 4242", result.CardLast4)
		}
		if result.CardBrand != "visa" {
			t.Fatalf("card brand = %s, want visa", result.CardBrand)
		}

		refund := gw.ProcessRefund(RefundRequest{
			PaymentTransactionID: "txn-1",
			PaymentIntentID:      "pi-1",
			Amount:               decimal.RequireFromString("1.0000"),
			Currency:             "USD",
		})
		if !refund.Success {
			t.Fatalf("expected refund success, got %+v", refund)
		}
	})

	t.Run("failure-rate path fails when set to one", func(t *testing.T) {
		gw := NewMockGateway(true, 0, 1.0)
		if result := gw.ProcessPayment(PaymentRequest{}); result.Success {
			t.Fatalf("expected payment failure, got %+v", result)
		}
		if result := gw.ProcessRefund(RefundRequest{}); result.Success {
			t.Fatalf("expected refund failure, got %+v", result)
		}
	})

	t.Run("stripe and paypal stubs fail fast", func(t *testing.T) {
		stripe := NewStripeGateway(true)
		if result := stripe.ProcessPayment(PaymentRequest{}); result.Success || result.ErrorCode != "not_implemented" {
			t.Fatalf("stripe payment result = %+v, want not_implemented failure", result)
		}
		if result := stripe.ProcessRefund(RefundRequest{}); result.Success || result.ErrorCode != "not_implemented" {
			t.Fatalf("stripe refund result = %+v, want not_implemented failure", result)
		}

		paypal := NewPayPalGateway(true)
		if result := paypal.ProcessPayment(PaymentRequest{}); result.Success || result.ErrorCode != "not_implemented" {
			t.Fatalf("paypal payment result = %+v, want not_implemented failure", result)
		}
		if result := paypal.ProcessRefund(RefundRequest{}); result.Success || result.ErrorCode != "not_implemented" {
			t.Fatalf("paypal refund result = %+v, want not_implemented failure", result)
		}
	})
}
