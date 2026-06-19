package payment

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"
)

type paymentStore interface {
	CreatePayment(ctx context.Context, p *Payment) error
	UpdatePayment(ctx context.Context, p *Payment) error
	GetPayment(ctx context.Context, id uuid.UUID) (*Payment, error)
	GetPaymentForUpdate(ctx context.Context, id uuid.UUID) (*Payment, error)
	GetPaymentByOrderID(ctx context.Context, orderID string) (*Payment, error)
	GetPaymentByIdempotencyKey(ctx context.Context, idempotencyKey string) (*Payment, error)
	GetPaymentsByCustomer(ctx context.Context, customerID string) ([]*Payment, error)
	CreateRefund(ctx context.Context, refund *Refund) error
	UpdateRefund(ctx context.Context, refund *Refund) error
	GetRefund(ctx context.Context, id uuid.UUID) (*Refund, error)
	GetRefundByCorrelationID(ctx context.Context, correlationID string) (*Refund, error)
	GetRefundsByPayment(ctx context.Context, paymentID uuid.UUID) ([]*Refund, error)
	CreateTransaction(ctx context.Context, tx *Transaction) error
}

type dbQuerier interface {
	Exec(ctx context.Context, sql string, arguments ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, arguments ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, arguments ...any) pgx.Row
}

type Store struct {
	pool *pgxpool.Pool
	db   dbQuerier
}

func NewStore(db *pgxpool.Pool) *Store {
	return &Store{pool: db, db: db}
}

func (s *Store) Ping(ctx context.Context) error {
	if s == nil || s.pool == nil {
		return errors.New("database is not configured")
	}
	return s.pool.Ping(ctx)
}

func (s *Store) RunInTx(ctx context.Context, fn func(paymentStore) error) (err error) {
	if s == nil || s.pool == nil {
		return errors.New("database is not configured")
	}
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	txStore := &Store{pool: s.pool, db: tx}
	defer func() {
		if r := recover(); r != nil {
			_ = tx.Rollback(ctx)
			panic(r)
		}
	}()
	if err := fn(txStore); err != nil {
		if rbErr := tx.Rollback(ctx); rbErr != nil {
			return fmt.Errorf("%w (additionally, rollback failed: %v)", err, rbErr)
		}
		return err
	}
	return tx.Commit(ctx)
}

func (s *Store) CreatePayment(ctx context.Context, p *Payment) error {
	if p == nil {
		return errors.New("payment is nil")
	}
	if p.ID == uuid.Nil {
		p.ID = uuid.New()
	}
	now := time.Now().UTC()
	if p.CreatedAt.IsZero() {
		p.CreatedAt = now
	}
	p.UpdatedAt = sql.NullTime{Time: now, Valid: true}

	_, err := s.db.Exec(ctx, `
INSERT INTO payments (
    id, order_id, customer_id, amount, currency, status, gateway,
    gateway_transaction_id, gateway_payment_intent_id, payment_method_id,
    card_last4, card_brand, metadata, failure_reason, failure_code,
    created_at, processed_at, completed_at, updated_at, correlation_id, idempotency_key
) VALUES (
    $1, $2, $3, $4::numeric, $5, $6, $7,
    $8, $9, $10, $11, $12, $13, $14, $15,
    $16, $17, $18, $19, $20, $21
)`,
		p.ID,
		p.OrderID,
		p.CustomerID,
		p.Amount.String(),
		p.Currency,
		string(p.Status),
		p.Gateway,
		nullStringArg(p.GatewayTransactionID),
		nullStringArg(p.GatewayPaymentIntentID),
		nullUUIDArg(p.PaymentMethodID),
		nullStringArg(p.CardLast4),
		nullStringArg(p.CardBrand),
		nullStringArg(p.Metadata),
		nullStringArg(p.FailureReason),
		nullStringArg(p.FailureCode),
		p.CreatedAt,
		nullTimeArg(p.ProcessedAt),
		nullTimeArg(p.CompletedAt),
		p.UpdatedAt.Time,
		nullStringArg(p.CorrelationID),
		nullStringArg(p.IdempotencyKey),
	)
	return err
}

func (s *Store) UpdatePayment(ctx context.Context, p *Payment) error {
	if p == nil {
		return errors.New("payment is nil")
	}
	now := time.Now().UTC()
	p.UpdatedAt = sql.NullTime{Time: now, Valid: true}

	tag, err := s.db.Exec(ctx, `
UPDATE payments
SET order_id = $2,
    customer_id = $3,
    amount = $4::numeric,
    currency = $5,
    status = $6,
    gateway = $7,
    gateway_transaction_id = $8,
    gateway_payment_intent_id = $9,
    payment_method_id = $10,
    card_last4 = $11,
    card_brand = $12,
    metadata = $13,
    failure_reason = $14,
    failure_code = $15,
    processed_at = $16,
    completed_at = $17,
    updated_at = $18,
    correlation_id = $19,
    idempotency_key = $20
WHERE id = $1`,
		p.ID,
		p.OrderID,
		p.CustomerID,
		p.Amount.String(),
		p.Currency,
		string(p.Status),
		p.Gateway,
		nullStringArg(p.GatewayTransactionID),
		nullStringArg(p.GatewayPaymentIntentID),
		nullUUIDArg(p.PaymentMethodID),
		nullStringArg(p.CardLast4),
		nullStringArg(p.CardBrand),
		nullStringArg(p.Metadata),
		nullStringArg(p.FailureReason),
		nullStringArg(p.FailureCode),
		nullTimeArg(p.ProcessedAt),
		nullTimeArg(p.CompletedAt),
		p.UpdatedAt.Time,
		nullStringArg(p.CorrelationID),
		nullStringArg(p.IdempotencyKey),
	)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return pgx.ErrNoRows
	}
	return nil
}

func (s *Store) GetPayment(ctx context.Context, id uuid.UUID) (*Payment, error) {
	row := s.db.QueryRow(ctx, `
SELECT
    id, order_id, customer_id, amount::text, currency, status, gateway,
    gateway_transaction_id, gateway_payment_intent_id, payment_method_id::text,
    card_last4, card_brand, metadata, failure_reason, failure_code,
    created_at, processed_at, completed_at, updated_at, correlation_id, idempotency_key
FROM payments
WHERE id = $1`, id)
	return scanPaymentRow(row)
}

func (s *Store) GetPaymentForUpdate(ctx context.Context, id uuid.UUID) (*Payment, error) {
	row := s.db.QueryRow(ctx, `
SELECT
    id, order_id, customer_id, amount::text, currency, status, gateway,
    gateway_transaction_id, gateway_payment_intent_id, payment_method_id::text,
    card_last4, card_brand, metadata, failure_reason, failure_code,
    created_at, processed_at, completed_at, updated_at, correlation_id, idempotency_key
FROM payments
WHERE id = $1
FOR UPDATE`, id)
	return scanPaymentRow(row)
}

func (s *Store) GetPaymentByOrderID(ctx context.Context, orderID string) (*Payment, error) {
	row := s.db.QueryRow(ctx, `
SELECT
    id, order_id, customer_id, amount::text, currency, status, gateway,
    gateway_transaction_id, gateway_payment_intent_id, payment_method_id::text,
    card_last4, card_brand, metadata, failure_reason, failure_code,
    created_at, processed_at, completed_at, updated_at, correlation_id, idempotency_key
FROM payments
WHERE order_id = $1
ORDER BY created_at DESC
LIMIT 1`, orderID)
	return scanPaymentRow(row)
}

func (s *Store) GetPaymentByIdempotencyKey(ctx context.Context, idempotencyKey string) (*Payment, error) {
	row := s.db.QueryRow(ctx, `
SELECT
    id, order_id, customer_id, amount::text, currency, status, gateway,
    gateway_transaction_id, gateway_payment_intent_id, payment_method_id::text,
    card_last4, card_brand, metadata, failure_reason, failure_code,
    created_at, processed_at, completed_at, updated_at, correlation_id, idempotency_key
FROM payments
WHERE idempotency_key = $1
ORDER BY created_at DESC
LIMIT 1`, idempotencyKey)
	return scanPaymentRow(row)
}

func (s *Store) GetPaymentsByCustomer(ctx context.Context, customerID string) ([]*Payment, error) {
	rows, err := s.db.Query(ctx, `
SELECT
    id, order_id, customer_id, amount::text, currency, status, gateway,
    gateway_transaction_id, gateway_payment_intent_id, payment_method_id::text,
    card_last4, card_brand, metadata, failure_reason, failure_code,
    created_at, processed_at, completed_at, updated_at, correlation_id, idempotency_key
FROM payments
WHERE customer_id = $1
ORDER BY created_at DESC`, customerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var payments []*Payment
	for rows.Next() {
		payment, err := scanPaymentRow(rows)
		if err != nil {
			return nil, err
		}
		payments = append(payments, payment)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return payments, nil
}

func (s *Store) CreateRefund(ctx context.Context, refund *Refund) error {
	if refund == nil {
		return errors.New("refund is nil")
	}
	if refund.ID == uuid.Nil {
		refund.ID = uuid.New()
	}
	now := time.Now().UTC()
	if refund.CreatedAt.IsZero() {
		refund.CreatedAt = now
	}

	_, err := s.db.Exec(ctx, `
INSERT INTO refunds (
    id, payment_id, amount, currency, status, reason, gateway_refund_id,
    failure_reason, failure_code, initiated_by, created_at, processed_at,
    completed_at, correlation_id
) VALUES (
    $1, $2, $3::numeric, $4, $5, $6, $7,
    $8, $9, $10, $11, $12, $13, $14
)`,
		refund.ID,
		refund.PaymentID,
		refund.Amount.String(),
		refund.Currency,
		string(refund.Status),
		nullStringArg(refund.Reason),
		nullStringArg(refund.GatewayRefundID),
		nullStringArg(refund.FailureReason),
		nullStringArg(refund.FailureCode),
		nullStringArg(refund.InitiatedBy),
		refund.CreatedAt,
		nullTimeArg(refund.ProcessedAt),
		nullTimeArg(refund.CompletedAt),
		nullStringArg(refund.CorrelationID),
	)
	return err
}

func (s *Store) UpdateRefund(ctx context.Context, refund *Refund) error {
	if refund == nil {
		return errors.New("refund is nil")
	}

	tag, err := s.db.Exec(ctx, `
UPDATE refunds
SET payment_id = $2,
    amount = $3::numeric,
    currency = $4,
    status = $5,
    reason = $6,
    gateway_refund_id = $7,
    failure_reason = $8,
    failure_code = $9,
    initiated_by = $10,
    processed_at = $11,
    completed_at = $12,
    correlation_id = $13
WHERE id = $1`,
		refund.ID,
		refund.PaymentID,
		refund.Amount.String(),
		refund.Currency,
		string(refund.Status),
		nullStringArg(refund.Reason),
		nullStringArg(refund.GatewayRefundID),
		nullStringArg(refund.FailureReason),
		nullStringArg(refund.FailureCode),
		nullStringArg(refund.InitiatedBy),
		nullTimeArg(refund.ProcessedAt),
		nullTimeArg(refund.CompletedAt),
		nullStringArg(refund.CorrelationID),
	)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return pgx.ErrNoRows
	}
	return nil
}

func (s *Store) GetRefund(ctx context.Context, id uuid.UUID) (*Refund, error) {
	row := s.db.QueryRow(ctx, `
SELECT
    id, payment_id, amount::text, currency, status, reason, gateway_refund_id,
    failure_reason, failure_code, created_at, processed_at, completed_at,
    correlation_id, initiated_by
FROM refunds
WHERE id = $1`, id)
	return scanRefundRow(row)
}

func (s *Store) GetRefundByCorrelationID(ctx context.Context, correlationID string) (*Refund, error) {
	row := s.db.QueryRow(ctx, `
SELECT
    id, payment_id, amount::text, currency, status, reason, gateway_refund_id,
    failure_reason, failure_code, created_at, processed_at, completed_at,
    correlation_id, initiated_by
FROM refunds
WHERE correlation_id = $1
ORDER BY created_at DESC
LIMIT 1`, correlationID)
	return scanRefundRow(row)
}

func (s *Store) GetRefundsByPayment(ctx context.Context, paymentID uuid.UUID) ([]*Refund, error) {
	rows, err := s.db.Query(ctx, `
SELECT
    id, payment_id, amount::text, currency, status, reason, gateway_refund_id,
    failure_reason, failure_code, created_at, processed_at, completed_at,
    correlation_id, initiated_by
FROM refunds
WHERE payment_id = $1
ORDER BY created_at DESC`, paymentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var refunds []*Refund
	for rows.Next() {
		refund, err := scanRefundRow(rows)
		if err != nil {
			return nil, err
		}
		refunds = append(refunds, refund)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return refunds, nil
}

func (s *Store) CreateTransaction(ctx context.Context, tx *Transaction) error {
	if tx == nil {
		return errors.New("transaction is nil")
	}
	if tx.ID == uuid.Nil {
		tx.ID = uuid.New()
	}
	if tx.CreatedAt.IsZero() {
		tx.CreatedAt = time.Now().UTC()
	}

	_, err := s.db.Exec(ctx, `
INSERT INTO transactions (
    id, payment_id, refund_id, type, amount, currency, success,
    gateway_transaction_id, gateway_response, gateway_error_code, gateway_error_message,
    created_at, correlation_id, ip_address, user_agent
) VALUES (
    $1, $2, $3, $4, $5::numeric, $6, $7,
    $8, $9, $10, $11,
    $12, $13, $14, $15
)`,
		tx.ID,
		tx.PaymentID,
		nullUUIDArg(tx.RefundID),
		string(tx.Type),
		tx.Amount.String(),
		tx.Currency,
		tx.Success,
		nullStringArg(tx.GatewayTransactionID),
		nullStringArg(tx.GatewayResponse),
		nullStringArg(tx.GatewayErrorCode),
		nullStringArg(tx.GatewayErrorMessage),
		tx.CreatedAt,
		nullStringArg(tx.CorrelationID),
		nullStringArg(tx.IPAddress),
		nullStringArg(tx.UserAgent),
	)
	return err
}

func scanPaymentRow(row pgx.Row) (*Payment, error) {
	var (
		id                     uuid.UUID
		orderID                string
		customerID             string
		amountText             string
		currency               string
		status                 string
		gateway                string
		gatewayTransactionID   sql.NullString
		gatewayPaymentIntentID sql.NullString
		paymentMethodIDText    sql.NullString
		cardLast4              sql.NullString
		cardBrand              sql.NullString
		metadata               sql.NullString
		failureReason          sql.NullString
		failureCode            sql.NullString
		createdAt              time.Time
		processedAt            sql.NullTime
		completedAt            sql.NullTime
		updatedAt              sql.NullTime
		correlationID          sql.NullString
		idempotencyKey         sql.NullString
	)

	if err := row.Scan(
		&id,
		&orderID,
		&customerID,
		&amountText,
		&currency,
		&status,
		&gateway,
		&gatewayTransactionID,
		&gatewayPaymentIntentID,
		&paymentMethodIDText,
		&cardLast4,
		&cardBrand,
		&metadata,
		&failureReason,
		&failureCode,
		&createdAt,
		&processedAt,
		&completedAt,
		&updatedAt,
		&correlationID,
		&idempotencyKey,
	); err != nil {
		return nil, err
	}

	amount, err := decimal.NewFromString(amountText)
	if err != nil {
		return nil, fmt.Errorf("parse payment amount: %w", err)
	}

	paymentMethodID := uuid.NullUUID{}
	if paymentMethodIDText.Valid {
		parsed, err := uuid.Parse(paymentMethodIDText.String)
		if err != nil {
			return nil, fmt.Errorf("parse payment_method_id: %w", err)
		}
		paymentMethodID = uuid.NullUUID{UUID: parsed, Valid: true}
	}

	return &Payment{
		ID:                     id,
		OrderID:                orderID,
		CustomerID:             customerID,
		Amount:                 amount,
		Currency:               currency,
		Status:                 PaymentStatus(status),
		Gateway:                gateway,
		GatewayTransactionID:   gatewayTransactionID,
		GatewayPaymentIntentID: gatewayPaymentIntentID,
		PaymentMethodID:        paymentMethodID,
		CardLast4:              cardLast4,
		CardBrand:              cardBrand,
		Metadata:               metadata,
		FailureReason:          failureReason,
		FailureCode:            failureCode,
		CreatedAt:              createdAt,
		ProcessedAt:            processedAt,
		CompletedAt:            completedAt,
		UpdatedAt:              updatedAt,
		CorrelationID:          correlationID,
		IdempotencyKey:         idempotencyKey,
	}, nil
}

func scanRefundRow(row pgx.Row) (*Refund, error) {
	var (
		id              uuid.UUID
		paymentID       uuid.UUID
		amountText      string
		currency        string
		status          string
		reason          sql.NullString
		gatewayRefundID sql.NullString
		failureReason   sql.NullString
		failureCode     sql.NullString
		createdAt       time.Time
		processedAt     sql.NullTime
		completedAt     sql.NullTime
		correlationID   sql.NullString
		initiatedBy     sql.NullString
	)

	if err := row.Scan(
		&id,
		&paymentID,
		&amountText,
		&currency,
		&status,
		&reason,
		&gatewayRefundID,
		&failureReason,
		&failureCode,
		&createdAt,
		&processedAt,
		&completedAt,
		&correlationID,
		&initiatedBy,
	); err != nil {
		return nil, err
	}

	amount, err := decimal.NewFromString(amountText)
	if err != nil {
		return nil, fmt.Errorf("parse refund amount: %w", err)
	}

	return &Refund{
		ID:              id,
		PaymentID:       paymentID,
		Amount:          amount,
		Currency:        currency,
		Status:          RefundStatus(status),
		Reason:          reason,
		GatewayRefundID: gatewayRefundID,
		FailureReason:   failureReason,
		FailureCode:     failureCode,
		CreatedAt:       createdAt,
		ProcessedAt:     processedAt,
		CompletedAt:     completedAt,
		CorrelationID:   correlationID,
		InitiatedBy:     initiatedBy,
	}, nil
}

func nullStringArg(ns sql.NullString) any {
	if ns.Valid {
		return ns.String
	}
	return nil
}

func nullTimeArg(nt sql.NullTime) any {
	if nt.Valid {
		return nt.Time.UTC()
	}
	return nil
}

func nullUUIDArg(nu uuid.NullUUID) any {
	if nu.Valid {
		return nu.UUID
	}
	return nil
}
