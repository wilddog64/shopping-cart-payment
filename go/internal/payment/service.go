package payment

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/shopspring/decimal"

	"github.com/wilddog64/shopping-cart-payment/go/internal/gateway"
)

type PaymentService struct {
	store  paymentStore
	router *gateway.Router
}

func NewPaymentService(store paymentStore, router *gateway.Router) *PaymentService {
	return &PaymentService{store: store, router: router}
}

type paymentTransactionRunner interface {
	RunInTx(ctx context.Context, fn func(paymentStore) error) error
}

func (s *PaymentService) ProcessPayment(ctx context.Context, req ProcessPaymentRequest, correlationID, idempotencyKey string) (*Payment, error) {
	gatewayImpl, err := s.router.GetGatewayOrDefault(req.Gateway)
	if err != nil {
		return nil, err
	}

	if strings.TrimSpace(idempotencyKey) != "" {
		if existing, err := s.store.GetPaymentByIdempotencyKey(ctx, idempotencyKey); err == nil {
			return existing, nil
		} else if !errors.Is(err, pgx.ErrNoRows) {
			return nil, err
		}
	}

	if existing, err := s.store.GetPaymentByOrderID(ctx, req.OrderID); err == nil {
		if existing.Status == PaymentStatusCompleted {
			return existing, nil
		}
	} else if !errors.Is(err, pgx.ErrNoRows) {
		return nil, err
	}

	paymentMethodID := uuid.NullUUID{}
	if parsed, err := uuid.Parse(strings.TrimSpace(req.PaymentMethodID)); err == nil {
		paymentMethodID = uuid.NullUUID{UUID: parsed, Valid: true}
	}

	now := time.Now().UTC()
	payment := &Payment{
		ID:              uuid.New(),
		OrderID:         req.OrderID,
		CustomerID:      req.CustomerID,
		Amount:          req.Amount,
		Currency:        strings.ToUpper(req.Currency),
		Status:          PaymentStatusPending,
		Gateway:         gatewayImpl.GetName(),
		PaymentMethodID: paymentMethodID,
		CorrelationID:   nullString(correlationID),
		IdempotencyKey:  nullString(idempotencyKey),
		CreatedAt:       now,
		UpdatedAt:       sql.NullTime{Time: now, Valid: true},
	}

	request := gateway.PaymentRequest{
		OrderID:             req.OrderID,
		CustomerID:          req.CustomerID,
		Amount:              req.Amount,
		Currency:            strings.ToUpper(req.Currency),
		PaymentMethodToken:  req.PaymentMethodID,
		CardNumber:          req.CardNumber,
		CardExpMonth:        req.CardExpMonth,
		CardExpYear:         req.CardExpYear,
		CardCVC:             req.CardCvc,
		CardholderName:      req.CardholderName,
		BillingEmail:        req.BillingEmail,
		BillingAddressLine1: req.BillingAddressLine1,
		BillingAddressLine2: req.BillingAddressLine2,
		BillingCity:         req.BillingCity,
		BillingState:        req.BillingState,
		BillingPostalCode:   req.BillingPostalCode,
		BillingCountry:      req.BillingCountry,
		IdempotencyKey:      idempotencyKey,
		CorrelationID:       correlationID,
	}

	result := gatewayImpl.ProcessPayment(request)
	persist := func(store paymentStore) error {
		return persistProcessedPayment(ctx, store, payment, req, correlationID, result, now)
	}

	var persistErr error
	if runner, ok := s.store.(paymentTransactionRunner); ok {
		persistErr = runner.RunInTx(ctx, persist)
	} else {
		persistErr = persist(s.store)
	}
	if persistErr != nil {
		if isUniqueViolation(persistErr) && strings.TrimSpace(idempotencyKey) != "" {
			if existing, getErr := s.store.GetPaymentByIdempotencyKey(ctx, idempotencyKey); getErr == nil {
				return existing, nil
			}
		}
		return nil, persistErr
	}
	return payment, nil
}

func (s *PaymentService) GetPayment(ctx context.Context, paymentID uuid.UUID) (*Payment, error) {
	payment, err := s.store.GetPayment(ctx, paymentID)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrPaymentNotFound
	}
	return payment, err
}

func (s *PaymentService) GetPaymentByOrderID(ctx context.Context, orderID string) (*Payment, error) {
	payment, err := s.store.GetPaymentByOrderID(ctx, orderID)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrPaymentNotFound
	}
	return payment, err
}

func (s *PaymentService) GetPaymentsByCustomer(ctx context.Context, customerID string) ([]*Payment, error) {
	return s.store.GetPaymentsByCustomer(ctx, customerID)
}

func (s *PaymentService) UpdatePaymentStatus(ctx context.Context, paymentID uuid.UUID, status PaymentStatus) (*Payment, error) {
	payment, err := s.GetPayment(ctx, paymentID)
	if err != nil {
		return nil, err
	}
	payment.Status = status
	switch status {
	case PaymentStatusProcessing:
		payment.ProcessedAt = sql.NullTime{Time: time.Now().UTC(), Valid: true}
	case PaymentStatusCompleted:
		payment.CompletedAt = sql.NullTime{Time: time.Now().UTC(), Valid: true}
	}
	if err := s.store.UpdatePayment(ctx, payment); err != nil {
		return nil, err
	}
	return payment, nil
}

func nullString(value string) sql.NullString {
	value = strings.TrimSpace(value)
	if value == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: value, Valid: true}
}

func persistProcessedPayment(ctx context.Context, store paymentStore, payment *Payment, req ProcessPaymentRequest, correlationID string, result gateway.PaymentResult, now time.Time) error {
	if err := store.CreatePayment(ctx, payment); err != nil {
		return err
	}

	payment.Status = PaymentStatusProcessing
	payment.ProcessedAt = sql.NullTime{Time: now, Valid: true}
	if err := store.UpdatePayment(ctx, payment); err != nil {
		return err
	}

	if err := store.CreateTransaction(ctx, &Transaction{
		ID:                   uuid.New(),
		PaymentID:            payment.ID,
		Type:                 TransactionTypeCharge,
		Amount:               req.Amount,
		Currency:             strings.ToUpper(req.Currency),
		Success:              result.Success,
		GatewayTransactionID: nullString(result.TransactionID),
		GatewayResponse:      nullString(result.RawResponse),
		GatewayErrorCode:     nullString(result.ErrorCode),
		GatewayErrorMessage:  nullString(result.ErrorMessage),
		CreatedAt:            now,
		CorrelationID:        nullString(correlationID),
	}); err != nil {
		return err
	}

	if result.Success {
		payment.Status = PaymentStatusCompleted
		payment.GatewayTransactionID = nullString(result.TransactionID)
		payment.GatewayPaymentIntentID = nullString(result.PaymentIntentID)
		payment.CardLast4 = nullString(result.CardLast4)
		payment.CardBrand = nullString(result.CardBrand)
		payment.CompletedAt = sql.NullTime{Time: time.Now().UTC(), Valid: true}
		payment.FailureReason = sql.NullString{}
		payment.FailureCode = sql.NullString{}
	} else {
		payment.Status = PaymentStatusFailed
		payment.FailureCode = nullString(result.ErrorCode)
		payment.FailureReason = nullString(result.ErrorMessage)
	}

	if err := store.UpdatePayment(ctx, payment); err != nil {
		return err
	}
	return nil
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code == "23505"
	}
	return false
}

func decimalCmpPositive(amount decimal.Decimal) bool {
	return amount.Cmp(decimal.Zero) > 0
}
