package gateway

import "github.com/shopspring/decimal"

type PaymentRequest struct {
	OrderID             string
	CustomerID          string
	Amount              decimal.Decimal
	Currency            string
	PaymentMethodToken  string
	CardNumber          string
	CardExpMonth        string
	CardExpYear         string
	CardCVC             string
	CardholderName      string
	BillingEmail        string
	BillingAddressLine1 string
	BillingAddressLine2 string
	BillingCity         string
	BillingState        string
	BillingPostalCode   string
	BillingCountry      string
	Description         string
	IdempotencyKey      string
	CorrelationID       string
}

type PaymentResult struct {
	Success         bool
	TransactionID   string
	PaymentIntentID string
	Status          string
	CardLast4       string
	CardBrand       string
	ErrorCode       string
	ErrorMessage    string
	RawResponse     string
}

type RefundRequest struct {
	PaymentTransactionID string
	PaymentIntentID      string
	Amount               decimal.Decimal
	Currency             string
	Reason               string
	IdempotencyKey       string
	CorrelationID        string
}

type RefundResult struct {
	Success      bool
	RefundID     string
	Status       string
	ErrorCode    string
	ErrorMessage string
	RawResponse  string
}

type TokenizeRequest struct {
	CustomerID          string
	CardNumber          string
	CardExpMonth        string
	CardExpYear         string
	CardCVC             string
	CardholderName      string
	BillingEmail        string
	BillingAddressLine1 string
	BillingCity         string
	BillingState        string
	BillingPostalCode   string
	BillingCountry      string
}

type TokenizeResult struct {
	Success      bool
	Token        string
	Last4        string
	Brand        string
	ExpMonth     string
	ExpYear      string
	ErrorCode    string
	ErrorMessage string
}
