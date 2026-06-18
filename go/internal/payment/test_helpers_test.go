package payment

import (
	"context"
	"database/sql"
	"errors"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/shopspring/decimal"
)

type fakeStore struct {
	mu sync.Mutex

	paymentsByID          map[uuid.UUID]*Payment
	paymentsByOrder       map[string]*Payment
	paymentsByIdempotency map[string]*Payment
	paymentsByCustomer    map[string][]*Payment
	refundsByID           map[uuid.UUID]*Refund
	refundsByPayment      map[uuid.UUID][]*Refund
	refundsByCorrelation  map[string]*Refund
	transactions          []*Transaction

	createPaymentCalls int
	updatePaymentCalls int
	createRefundCalls  int
	updateRefundCalls  int
	createTxCalls      int
}

func newFakeStore() *fakeStore {
	return &fakeStore{
		paymentsByID:          map[uuid.UUID]*Payment{},
		paymentsByOrder:       map[string]*Payment{},
		paymentsByIdempotency: map[string]*Payment{},
		paymentsByCustomer:    map[string][]*Payment{},
		refundsByID:           map[uuid.UUID]*Refund{},
		refundsByPayment:      map[uuid.UUID][]*Refund{},
		refundsByCorrelation:  map[string]*Refund{},
	}
}

func (s *fakeStore) CreatePayment(_ context.Context, p *Payment) error {
	if p == nil {
		return errors.New("payment is nil")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.createPaymentCalls++
	s.paymentsByID[p.ID] = p
	s.paymentsByOrder[p.OrderID] = p
	if p.IdempotencyKey.Valid && strings.TrimSpace(p.IdempotencyKey.String) != "" {
		s.paymentsByIdempotency[p.IdempotencyKey.String] = p
	}
	s.paymentsByCustomer[p.CustomerID] = append(s.paymentsByCustomer[p.CustomerID], p)
	return nil
}

func (s *fakeStore) UpdatePayment(_ context.Context, p *Payment) error {
	if p == nil {
		return errors.New("payment is nil")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.updatePaymentCalls++
	if _, ok := s.paymentsByID[p.ID]; !ok {
		return pgx.ErrNoRows
	}
	s.paymentsByID[p.ID] = p
	s.paymentsByOrder[p.OrderID] = p
	if p.IdempotencyKey.Valid && strings.TrimSpace(p.IdempotencyKey.String) != "" {
		s.paymentsByIdempotency[p.IdempotencyKey.String] = p
	}
	for customerID, payments := range s.paymentsByCustomer {
		for idx, payment := range payments {
			if payment.ID == p.ID {
				s.paymentsByCustomer[customerID][idx] = p
			}
		}
	}
	return nil
}

func (s *fakeStore) GetPayment(_ context.Context, id uuid.UUID) (*Payment, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	payment, ok := s.paymentsByID[id]
	if !ok {
		return nil, pgx.ErrNoRows
	}
	return payment, nil
}

func (s *fakeStore) GetPaymentByOrderID(_ context.Context, orderID string) (*Payment, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	payment, ok := s.paymentsByOrder[orderID]
	if !ok {
		return nil, pgx.ErrNoRows
	}
	return payment, nil
}

func (s *fakeStore) GetPaymentByIdempotencyKey(_ context.Context, idempotencyKey string) (*Payment, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	payment, ok := s.paymentsByIdempotency[idempotencyKey]
	if !ok {
		return nil, pgx.ErrNoRows
	}
	return payment, nil
}

func (s *fakeStore) GetPaymentsByCustomer(_ context.Context, customerID string) ([]*Payment, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	payments := append([]*Payment(nil), s.paymentsByCustomer[customerID]...)
	sort.Slice(payments, func(i, j int) bool {
		return payments[i].CreatedAt.After(payments[j].CreatedAt)
	})
	return payments, nil
}

func (s *fakeStore) CreateRefund(_ context.Context, refund *Refund) error {
	if refund == nil {
		return errors.New("refund is nil")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.createRefundCalls++
	s.refundsByID[refund.ID] = refund
	s.refundsByPayment[refund.PaymentID] = append(s.refundsByPayment[refund.PaymentID], refund)
	if refund.CorrelationID.Valid && strings.TrimSpace(refund.CorrelationID.String) != "" {
		s.refundsByCorrelation[refund.CorrelationID.String] = refund
	}
	return nil
}

func (s *fakeStore) UpdateRefund(_ context.Context, refund *Refund) error {
	if refund == nil {
		return errors.New("refund is nil")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.updateRefundCalls++
	if _, ok := s.refundsByID[refund.ID]; !ok {
		return pgx.ErrNoRows
	}
	s.refundsByID[refund.ID] = refund
	if refund.CorrelationID.Valid && strings.TrimSpace(refund.CorrelationID.String) != "" {
		s.refundsByCorrelation[refund.CorrelationID.String] = refund
	}
	for paymentID, refunds := range s.refundsByPayment {
		for idx, existing := range refunds {
			if existing.ID == refund.ID {
				s.refundsByPayment[paymentID][idx] = refund
			}
		}
	}
	return nil
}

func (s *fakeStore) GetRefund(_ context.Context, id uuid.UUID) (*Refund, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	refund, ok := s.refundsByID[id]
	if !ok {
		return nil, pgx.ErrNoRows
	}
	return refund, nil
}

func (s *fakeStore) GetRefundByCorrelationID(_ context.Context, correlationID string) (*Refund, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	refund, ok := s.refundsByCorrelation[correlationID]
	if !ok {
		return nil, pgx.ErrNoRows
	}
	return refund, nil
}

func (s *fakeStore) GetRefundsByPayment(_ context.Context, paymentID uuid.UUID) ([]*Refund, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	refunds := append([]*Refund(nil), s.refundsByPayment[paymentID]...)
	sort.Slice(refunds, func(i, j int) bool {
		return refunds[i].CreatedAt.After(refunds[j].CreatedAt)
	})
	return refunds, nil
}

func (s *fakeStore) CreateTransaction(_ context.Context, tx *Transaction) error {
	if tx == nil {
		return errors.New("transaction is nil")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.createTxCalls++
	s.transactions = append(s.transactions, tx)
	return nil
}

func seedCompletedPayment(store *fakeStore, amount string) *Payment {
	payment := &Payment{
		ID:                     uuid.New(),
		OrderID:                "order-" + uuid.NewString()[:8],
		CustomerID:             "customer-" + uuid.NewString()[:8],
		Amount:                 decimalFromString(amount),
		Currency:               "USD",
		Status:                 PaymentStatusCompleted,
		Gateway:                "mock",
		GatewayTransactionID:   sqlNullString("txn_" + uuid.NewString()[:8]),
		GatewayPaymentIntentID: sqlNullString("pi_" + uuid.NewString()[:8]),
		CardLast4:              sqlNullString("4242"),
		CardBrand:              sqlNullString("visa"),
		CreatedAt:              timeNowUTC(),
		CompletedAt:            sqlNullTime(timeNowUTC()),
		UpdatedAt:              sqlNullTime(timeNowUTC()),
	}
	_ = store.CreatePayment(context.Background(), payment)
	return payment
}

func decimalFromString(value string) decimal.Decimal {
	return decimal.RequireFromString(value)
}

func sqlNullString(value string) sql.NullString {
	value = strings.TrimSpace(value)
	if value == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: value, Valid: true}
}

func sqlNullTime(t time.Time) sql.NullTime {
	return sql.NullTime{Time: t, Valid: true}
}

func timeNowUTC() time.Time {
	return time.Now().UTC()
}
