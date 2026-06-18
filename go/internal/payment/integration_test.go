//go:build integration

package payment

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"

	"github.com/wilddog64/shopping-cart-payment/go/internal/gateway"
)

// This integration test is intentionally gated behind the `integration` build tag.
// Run it against a throwaway Postgres instance (for example via docker compose) by
// setting PAYMENT_INTEGRATION_DSN and applying the Java Flyway migrations.
func TestPaymentFlowIntegration(t *testing.T) {
	dsn := os.Getenv("PAYMENT_INTEGRATION_DSN")
	if dsn == "" {
		t.Skip("PAYMENT_INTEGRATION_DSN not set")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	db, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("connect postgres: %v", err)
	}
	defer db.Close()

	for _, rel := range []string{
		filepath.Join("..", "..", "..", "src", "main", "resources", "db", "migration", "V1__init_schema.sql"),
		filepath.Join("..", "..", "..", "src", "main", "resources", "db", "migration", "V2__add_billing_email_to_payment_methods.sql"),
	} {
		sqlBytes, err := os.ReadFile(rel)
		if err != nil {
			t.Fatalf("read migration %s: %v", rel, err)
		}
		if _, err := db.Exec(ctx, string(sqlBytes)); err != nil {
			t.Fatalf("apply migration %s: %v", rel, err)
		}
	}

	store := NewStore(db)
	router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 0))
	paymentSvc := NewPaymentService(store, router)
	refundSvc := NewRefundService(store, router)

	req := ProcessPaymentRequest{
		OrderID:    "integration-order-1",
		CustomerID: "integration-customer-1",
		Amount:     decimal.RequireFromString("42.5000"),
		Currency:   "USD",
		CardNumber: "4242424242424242",
	}

	payment, err := paymentSvc.ProcessPayment(ctx, req, "integration-corr-1", "integration-idem-1")
	if err != nil {
		t.Fatalf("process payment: %v", err)
	}
	if payment.Status != PaymentStatusCompleted {
		t.Fatalf("payment status = %s, want %s", payment.Status, PaymentStatusCompleted)
	}

	fetched, err := paymentSvc.GetPayment(ctx, payment.ID)
	if err != nil {
		t.Fatalf("get payment: %v", err)
	}
	if fetched.ID != payment.ID {
		t.Fatalf("get payment returned %s, want %s", fetched.ID, payment.ID)
	}

	listed, err := paymentSvc.GetPaymentsByCustomer(ctx, req.CustomerID)
	if err != nil {
		t.Fatalf("list payments: %v", err)
	}
	if len(listed) != 1 {
		t.Fatalf("payments by customer = %d, want 1", len(listed))
	}

	replayed, err := paymentSvc.ProcessPayment(ctx, req, "integration-corr-2", "integration-idem-1")
	if err != nil {
		t.Fatalf("idempotent replay: %v", err)
	}
	if replayed.ID != payment.ID {
		t.Fatalf("idempotent replay returned %s, want %s", replayed.ID, payment.ID)
	}

	refund, err := refundSvc.ProcessRefund(ctx, payment.ID, decimal.RequireFromString("10.0000"), "integration refund", "integration-user", "integration-refund-corr")
	if err != nil {
		t.Fatalf("process refund: %v", err)
	}
	if refund.Status != RefundStatusCompleted {
		t.Fatalf("refund status = %s, want %s", refund.Status, RefundStatusCompleted)
	}

	refunds, err := refundSvc.GetRefundsByPayment(ctx, payment.ID)
	if err != nil {
		t.Fatalf("refunds by payment: %v", err)
	}
	if len(refunds) != 1 {
		t.Fatalf("refund count = %d, want 1", len(refunds))
	}

	var paymentRows, refundRows, transactionRows int
	if err := db.QueryRow(ctx, `SELECT COUNT(*) FROM payments WHERE id = $1`, payment.ID).Scan(&paymentRows); err != nil {
		t.Fatalf("count payments: %v", err)
	}
	if err := db.QueryRow(ctx, `SELECT COUNT(*) FROM refunds WHERE payment_id = $1`, payment.ID).Scan(&refundRows); err != nil {
		t.Fatalf("count refunds: %v", err)
	}
	if err := db.QueryRow(ctx, `SELECT COUNT(*) FROM transactions WHERE payment_id = $1`, payment.ID).Scan(&transactionRows); err != nil {
		t.Fatalf("count transactions: %v", err)
	}
	if paymentRows != 1 || refundRows != 1 || transactionRows != 2 {
		t.Fatalf("row counts = payments:%d refunds:%d tx:%d", paymentRows, refundRows, transactionRows)
	}
}
