package payment

import (
	"context"
	"testing"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/shopspring/decimal"

	"github.com/wilddog64/shopping-cart-payment/go/internal/gateway"
)

func TestPaymentServiceProcessPaymentSuccessAndIdempotency(t *testing.T) {
	store := newFakeStore()
	router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 0))
	svc := NewPaymentService(store, router)

	req := ProcessPaymentRequest{
		OrderID:        "order-123",
		CustomerID:     "customer-123",
		Amount:         decimal.RequireFromString("42.5000"),
		Currency:       "usd",
		CardNumber:     "tok_test_4242",
		CardExpMonth:   "12",
		CardExpYear:    "2030",
		CardCvc:        "test-cvc",
		CardholderName: "Ada Lovelace",
	}

	payment, err := svc.ProcessPayment(context.Background(), req, "corr-1", "idem-1")
	if err != nil {
		t.Fatalf("ProcessPayment success: %v", err)
	}
	if payment.Currency != "USD" {
		t.Fatalf("currency = %s, want USD", payment.Currency)
	}
	if payment.Status != PaymentStatusCompleted {
		t.Fatalf("status = %s, want %s", payment.Status, PaymentStatusCompleted)
	}
	if got := payment.Amount.StringFixed(4); got != "42.5000" {
		t.Fatalf("amount = %s, want 42.5000", got)
	}
	if !payment.CompletedAt.Valid {
		t.Fatalf("completed_at not set")
	}
	if payment.FailureCode.Valid || payment.FailureReason.Valid {
		t.Fatalf("failure fields should be empty on success")
	}
	if !payment.CardLast4.Valid || payment.CardLast4.String != "4242" {
		t.Fatalf("card_last4 = %+v, want 4242", payment.CardLast4)
	}
	if !payment.CardBrand.Valid || payment.CardBrand.String != "visa" {
		t.Fatalf("card_brand = %+v, want visa", payment.CardBrand)
	}
	if len(store.transactions) != 1 {
		t.Fatalf("transactions = %d, want 1", len(store.transactions))
	}
	if store.transactions[0].Type != TransactionTypeCharge || !store.transactions[0].Success {
		t.Fatalf("charge audit row = %+v", store.transactions[0])
	}
	if store.createPaymentCalls != 1 {
		t.Fatalf("create payment calls = %d, want 1", store.createPaymentCalls)
	}

	replayed, err := svc.ProcessPayment(context.Background(), req, "corr-2", "idem-1")
	if err != nil {
		t.Fatalf("ProcessPayment replay: %v", err)
	}
	if replayed.ID != payment.ID {
		t.Fatalf("idempotent replay returned %s, want %s", replayed.ID, payment.ID)
	}
	if store.createPaymentCalls != 1 {
		t.Fatalf("idempotent replay created extra payment, calls=%d", store.createPaymentCalls)
	}
}

func TestPaymentServiceProcessPaymentFailure(t *testing.T) {
	store := newFakeStore()
	router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 1.0))
	svc := NewPaymentService(store, router)

	req := ProcessPaymentRequest{
		OrderID:    "order-fail",
		CustomerID: "customer-fail",
		Amount:     decimal.RequireFromString("10.0000"),
		Currency:   "usd",
		CardNumber: "tok_test_4242",
	}

	payment, err := svc.ProcessPayment(context.Background(), req, "", "idem-fail")
	if err != nil {
		t.Fatalf("ProcessPayment failure: %v", err)
	}
	if payment.Status != PaymentStatusFailed {
		t.Fatalf("status = %s, want %s", payment.Status, PaymentStatusFailed)
	}
	if !payment.FailureCode.Valid || payment.FailureCode.String != "processing_error" {
		t.Fatalf("failure_code = %+v", payment.FailureCode)
	}
	if !payment.FailureReason.Valid || payment.FailureReason.String != "Random mock failure for testing" {
		t.Fatalf("failure_reason = %+v", payment.FailureReason)
	}
	if payment.CompletedAt.Valid {
		t.Fatalf("completed_at should remain empty on failure")
	}
	if len(store.transactions) != 1 {
		t.Fatalf("transactions = %d, want 1", len(store.transactions))
	}
	if store.transactions[0].Type != TransactionTypeCharge || store.transactions[0].Success {
		t.Fatalf("failure audit row = %+v", store.transactions[0])
	}
}

func TestPaymentServiceProcessPaymentReturnsExistingOnUniqueViolation(t *testing.T) {
	store := newUniqueViolationStore()
	existing := &Payment{
		ID:          uuid.New(),
		OrderID:     "order-race",
		CustomerID:  "customer-race",
		Amount:      decimal.RequireFromString("15.0000"),
		Currency:    "USD",
		Status:      PaymentStatusCompleted,
		Gateway:     "mock",
		CreatedAt:   timeNowUTC(),
		CompletedAt: sqlNullTime(timeNowUTC()),
	}
	existing.IdempotencyKey = sqlNullString("idem-race")
	if err := store.CreatePayment(context.Background(), existing); err != nil {
		t.Fatalf("seed existing payment: %v", err)
	}

	router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 0))
	svc := NewPaymentService(store, router)

	req := ProcessPaymentRequest{
		OrderID:    existing.OrderID,
		CustomerID: existing.CustomerID,
		Amount:     existing.Amount,
		Currency:   existing.Currency,
		CardNumber: "tok_test_4242",
	}

	payment, err := svc.ProcessPayment(context.Background(), req, "corr-race", "idem-race")
	if err != nil {
		t.Fatalf("ProcessPayment unique violation: %v", err)
	}
	if payment.ID != existing.ID {
		t.Fatalf("payment ID = %s, want %s", payment.ID, existing.ID)
	}
	if store.createPaymentCalls != 1 {
		t.Fatalf("create payment calls = %d, want 1", store.createPaymentCalls)
	}
}

type uniqueViolationStore struct {
	*fakeStore
	orderLookups       int
	idempotencyLookups int
}

func newUniqueViolationStore() *uniqueViolationStore {
	return &uniqueViolationStore{fakeStore: newFakeStore()}
}

func (s *uniqueViolationStore) GetPaymentByOrderID(_ context.Context, orderID string) (*Payment, error) {
	s.orderLookups++
	if s.orderLookups == 1 {
		return nil, pgx.ErrNoRows
	}
	return s.fakeStore.GetPaymentByOrderID(context.Background(), orderID)
}

func (s *uniqueViolationStore) GetPaymentByIdempotencyKey(_ context.Context, idempotencyKey string) (*Payment, error) {
	s.idempotencyLookups++
	if s.idempotencyLookups == 1 {
		return nil, pgx.ErrNoRows
	}
	return s.fakeStore.GetPaymentByIdempotencyKey(context.Background(), idempotencyKey)
}

func (s *uniqueViolationStore) RunInTx(_ context.Context, fn func(paymentStore) error) error {
	return fn(uniqueViolationTxStore{s.fakeStore})
}

type uniqueViolationTxStore struct {
	paymentStore
}

func (s uniqueViolationTxStore) CreatePayment(context.Context, *Payment) error {
	return &pgconn.PgError{Code: "23505"}
}

func TestRefundServiceStatusMachinesAndGuards(t *testing.T) {
	t.Run("rejects non-completed payment", func(t *testing.T) {
		store := newFakeStore()
		router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 0))
		svc := NewRefundService(store, router)
		payment := &Payment{
			ID:         uuid.New(),
			OrderID:    "order-pending",
			CustomerID: "customer-pending",
			Amount:     decimal.RequireFromString("10.0000"),
			Currency:   "USD",
			Status:     PaymentStatusPending,
			Gateway:    "mock",
			CreatedAt:  timeNowUTC(),
		}
		if err := store.CreatePayment(context.Background(), payment); err != nil {
			t.Fatalf("seed payment: %v", err)
		}
		_, err := svc.ProcessRefund(context.Background(), payment.ID, decimal.RequireFromString("1.0000"), "n/a", "tester", "")
		if err != ErrRefundNotAllowed {
			t.Fatalf("err = %v, want ErrRefundNotAllowed", err)
		}
	})

	t.Run("rejects over-refund and handles partial/full refunds", func(t *testing.T) {
		store := newFakeStore()
		router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 0))
		svc := NewRefundService(store, router)

		partialPayment := seedCompletedPayment(store, "100.0000")
		store.refundsByPayment[partialPayment.ID] = []*Refund{
			{
				ID:        uuid.New(),
				PaymentID: partialPayment.ID,
				Amount:    decimal.RequireFromString("80.0000"),
				Status:    RefundStatusCompleted,
				CreatedAt: timeNowUTC(),
			},
		}
		if _, err := svc.ProcessRefund(context.Background(), partialPayment.ID, decimal.RequireFromString("30.0000"), "too much", "tester", "corr-over"); err != ErrRefundExceedsRemaining {
			t.Fatalf("over-refund err = %v, want ErrRefundExceedsRemaining", err)
		}

		refund, err := svc.ProcessRefund(context.Background(), partialPayment.ID, decimal.RequireFromString("10.0000"), "partial", "tester", "corr-partial")
		if err != nil {
			t.Fatalf("partial refund: %v", err)
		}
		if refund.Status != RefundStatusCompleted {
			t.Fatalf("partial refund status = %s", refund.Status)
		}
		payment, err := store.GetPayment(context.Background(), partialPayment.ID)
		if err != nil {
			t.Fatalf("load partial payment: %v", err)
		}
		if payment.Status != PaymentStatusCompleted {
			t.Fatalf("partial refund payment status = %s, want %s", payment.Status, PaymentStatusCompleted)
		}
		total, err := svc.GetTotalRefunded(context.Background(), partialPayment.ID)
		if err != nil {
			t.Fatalf("GetTotalRefunded: %v", err)
		}
		if got := total.StringFixed(4); got != "90.0000" {
			t.Fatalf("total refunded = %s, want 90.0000", got)
		}

		fullPayment := seedCompletedPayment(store, "25.0000")
		refund, err = svc.ProcessRefund(context.Background(), fullPayment.ID, decimal.RequireFromString("25.0000"), "full", "tester", "corr-full")
		if err != nil {
			t.Fatalf("full refund: %v", err)
		}
		if refund.Status != RefundStatusCompleted {
			t.Fatalf("full refund status = %s", refund.Status)
		}
		payment, err = store.GetPayment(context.Background(), fullPayment.ID)
		if err != nil {
			t.Fatalf("load full payment: %v", err)
		}
		if payment.Status != PaymentStatusRefunded {
			t.Fatalf("full refund payment status = %s, want %s", payment.Status, PaymentStatusRefunded)
		}
		if len(store.transactions) != 2 {
			t.Fatalf("transactions = %d, want 2", len(store.transactions))
		}
		if store.transactions[1].Type != TransactionTypeRefund || !store.transactions[1].Success {
			t.Fatalf("refund audit row = %+v", store.transactions[1])
		}
	})
}
