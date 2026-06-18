package payment

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/shopspring/decimal"

	"github.com/wilddog64/shopping-cart-payment/go/internal/gateway"
)

type RefundService struct {
	store  paymentStore
	router *gateway.Router
}

func NewRefundService(store paymentStore, router *gateway.Router) *RefundService {
	return &RefundService{store: store, router: router}
}

func (s *RefundService) ProcessRefund(ctx context.Context, paymentID uuid.UUID, amount decimal.Decimal, reason, initiatedBy, correlationID string) (*Refund, error) {
	if strings.TrimSpace(correlationID) != "" {
		if existing, err := s.store.GetRefundByCorrelationID(ctx, correlationID); err == nil {
			return existing, nil
		} else if !errors.Is(err, pgx.ErrNoRows) {
			return nil, err
		}
	}

	payment, err := s.store.GetPayment(ctx, paymentID)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrPaymentNotFound
	}
	if err != nil {
		return nil, err
	}

	if payment.Status != PaymentStatusCompleted && payment.Status != PaymentStatusRefundPending {
		return nil, ErrRefundNotAllowed
	}

	totalRefunded, err := s.GetTotalRefunded(ctx, paymentID)
	if err != nil {
		return nil, err
	}
	remaining := payment.Amount.Sub(totalRefunded)
	if amount.Cmp(remaining) > 0 {
		return nil, ErrRefundExceedsRemaining
	}

	gatewayImpl, err := s.router.GetGateway(payment.Gateway)
	if err != nil {
		return nil, err
	}

	now := time.Now().UTC()
	refund := &Refund{
		ID:            uuid.New(),
		PaymentID:     paymentID,
		Amount:        amount,
		Currency:      payment.Currency,
		Status:        RefundStatusPending,
		Reason:        nullString(reason),
		InitiatedBy:   nullString(initiatedBy),
		CorrelationID: nullString(correlationID),
		CreatedAt:     now,
	}
	if err := s.store.CreateRefund(ctx, refund); err != nil {
		return nil, err
	}

	payment.Status = PaymentStatusRefundPending
	if err := s.store.UpdatePayment(ctx, payment); err != nil {
		return nil, err
	}

	refund.Status = RefundStatusProcessing
	refund.ProcessedAt = sql.NullTime{Time: time.Now().UTC(), Valid: true}
	if err := s.store.UpdateRefund(ctx, refund); err != nil {
		return nil, err
	}

	gatewayRequest := gateway.RefundRequest{
		PaymentTransactionID: payment.GatewayTransactionID.String,
		PaymentIntentID:      payment.GatewayPaymentIntentID.String,
		Amount:               amount,
		Currency:             payment.Currency,
		Reason:               reason,
		CorrelationID:        correlationID,
	}
	result := gatewayImpl.ProcessRefund(gatewayRequest)

	if err := s.store.CreateTransaction(ctx, &Transaction{
		ID:                   uuid.New(),
		PaymentID:            paymentID,
		RefundID:             uuid.NullUUID{UUID: refund.ID, Valid: true},
		Type:                 TransactionTypeRefund,
		Amount:               amount,
		Currency:             payment.Currency,
		Success:              result.Success,
		GatewayTransactionID: nullString(result.RefundID),
		GatewayResponse:      nullString(result.RawResponse),
		GatewayErrorCode:     nullString(result.ErrorCode),
		GatewayErrorMessage:  nullString(result.ErrorMessage),
		CreatedAt:            time.Now().UTC(),
		CorrelationID:        nullString(correlationID),
	}); err != nil {
		return nil, err
	}

	if result.Success {
		refund.Status = RefundStatusCompleted
		refund.GatewayRefundID = nullString(result.RefundID)
		refund.CompletedAt = sql.NullTime{Time: time.Now().UTC(), Valid: true}
		refund.FailureReason = sql.NullString{}
		refund.FailureCode = sql.NullString{}
		if amount.Cmp(remaining) >= 0 {
			payment.Status = PaymentStatusRefunded
		} else {
			payment.Status = PaymentStatusCompleted
		}
		if err := s.store.UpdatePayment(ctx, payment); err != nil {
			return nil, err
		}
	} else {
		refund.Status = RefundStatusFailed
		refund.FailureCode = nullString(result.ErrorCode)
		refund.FailureReason = nullString(result.ErrorMessage)
		payment.Status = PaymentStatusRefundFailed
		if err := s.store.UpdatePayment(ctx, payment); err != nil {
			return nil, err
		}
	}

	if err := s.store.UpdateRefund(ctx, refund); err != nil {
		return nil, err
	}
	return refund, nil
}

func (s *RefundService) GetRefund(ctx context.Context, refundID uuid.UUID) (*Refund, error) {
	refund, err := s.store.GetRefund(ctx, refundID)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrRefundNotFound
	}
	return refund, err
}

func (s *RefundService) GetRefundsByPayment(ctx context.Context, paymentID uuid.UUID) ([]*Refund, error) {
	return s.store.GetRefundsByPayment(ctx, paymentID)
}

func (s *RefundService) GetTotalRefunded(ctx context.Context, paymentID uuid.UUID) (decimal.Decimal, error) {
	refunds, err := s.store.GetRefundsByPayment(ctx, paymentID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return decimal.Zero, nil
		}
		return decimal.Zero, err
	}
	total := decimal.Zero
	for _, refund := range refunds {
		if refund.Status == RefundStatusCompleted {
			total = total.Add(refund.Amount)
		}
	}
	return total, nil
}
