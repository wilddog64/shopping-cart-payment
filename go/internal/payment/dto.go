package payment

import (
	"database/sql"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

type ProcessPaymentRequest struct {
	OrderID             string          `json:"orderId" binding:"required"`
	CustomerID          string          `json:"customerId" binding:"required"`
	Amount              decimal.Decimal `json:"amount" binding:"required"`
	Currency            string          `json:"currency" binding:"required"`
	Gateway             string          `json:"gateway"`
	PaymentMethodID     string          `json:"paymentMethodId"`
	CardNumber          string          `json:"cardNumber"`
	CardExpMonth        string          `json:"cardExpMonth"`
	CardExpYear         string          `json:"cardExpYear"`
	CardCvc             string          `json:"cardCvc"`
	CardholderName      string          `json:"cardholderName"`
	BillingEmail        string          `json:"billingEmail"`
	BillingAddressLine1 string          `json:"billingAddressLine1"`
	BillingAddressLine2 string          `json:"billingAddressLine2"`
	BillingCity         string          `json:"billingCity"`
	BillingState        string          `json:"billingState"`
	BillingPostalCode   string          `json:"billingPostalCode"`
	BillingCountry      string          `json:"billingCountry"`
	IdempotencyKey      string          `json:"idempotencyKey"`
}

type RefundRequest struct {
	Amount decimal.Decimal `json:"amount" binding:"required"`
	Reason string          `json:"reason"`
}

type PaymentResponse struct {
	ID            uuid.UUID       `json:"id"`
	OrderID       string          `json:"orderId"`
	CustomerID    string          `json:"customerId"`
	Amount        decimal.Decimal `json:"amount"`
	Currency      string          `json:"currency"`
	Status        PaymentStatus   `json:"status"`
	Gateway       string          `json:"gateway"`
	CardLast4     *string         `json:"cardLast4"`
	CardBrand     *string         `json:"cardBrand"`
	FailureReason *string         `json:"failureReason"`
	CreatedAt     *string         `json:"createdAt"`
	CompletedAt   *string         `json:"completedAt"`
}

type RefundResponse struct {
	ID        uuid.UUID       `json:"id"`
	PaymentID uuid.UUID       `json:"paymentId"`
	Amount    decimal.Decimal `json:"amount"`
	Currency  string          `json:"currency"`
	Status    RefundStatus    `json:"status"`
	Reason    *string         `json:"reason"`
}

func PaymentResponseFrom(p *Payment) PaymentResponse {
	var cardLast4 *string
	if p.CardLast4.Valid {
		v := p.CardLast4.String
		cardLast4 = &v
	}
	var cardBrand *string
	if p.CardBrand.Valid {
		v := p.CardBrand.String
		cardBrand = &v
	}
	var failureReason *string
	if p.FailureReason.Valid {
		v := p.FailureReason.String
		failureReason = &v
	}
	return PaymentResponse{
		ID:            p.ID,
		OrderID:       p.OrderID,
		CustomerID:    p.CustomerID,
		Amount:        p.Amount,
		Currency:      p.Currency,
		Status:        p.Status,
		Gateway:       p.Gateway,
		CardLast4:     cardLast4,
		CardBrand:     cardBrand,
		FailureReason: failureReason,
		CreatedAt:     stringPtr(p.CreatedAt),
		CompletedAt:   timePtrOrNil(p.CompletedAt),
	}
}

func RefundResponseFrom(r *Refund) RefundResponse {
	var reason *string
	if r.Reason.Valid {
		v := r.Reason.String
		reason = &v
	}
	return RefundResponse{
		ID:        r.ID,
		PaymentID: r.PaymentID,
		Amount:    r.Amount,
		Currency:  r.Currency,
		Status:    r.Status,
		Reason:    reason,
	}
}

func stringPtr(t time.Time) *string {
	v := t.UTC().Format(time.RFC3339Nano)
	return &v
}

func timePtrOrNil(nt sql.NullTime) *string {
	if !nt.Valid {
		return nil
	}
	v := nt.Time.UTC().Format(time.RFC3339Nano)
	return &v
}
