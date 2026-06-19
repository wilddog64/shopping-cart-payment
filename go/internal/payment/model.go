package payment

import (
	"database/sql"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

type PaymentStatus string

const (
	PaymentStatusPending       PaymentStatus = "PENDING"
	PaymentStatusProcessing    PaymentStatus = "PROCESSING"
	PaymentStatusCompleted     PaymentStatus = "COMPLETED"
	PaymentStatusFailed        PaymentStatus = "FAILED"
	PaymentStatusRefundPending PaymentStatus = "REFUND_PENDING"
	PaymentStatusRefunded      PaymentStatus = "REFUNDED"
	PaymentStatusRefundFailed  PaymentStatus = "REFUND_FAILED"
)

type RefundStatus string

const (
	RefundStatusPending    RefundStatus = "PENDING"
	RefundStatusProcessing RefundStatus = "PROCESSING"
	RefundStatusCompleted  RefundStatus = "COMPLETED"
	RefundStatusFailed     RefundStatus = "FAILED"
)

type TransactionType string

const (
	TransactionTypeAuthorization TransactionType = "AUTHORIZATION"
	TransactionTypeCapture       TransactionType = "CAPTURE"
	TransactionTypeCharge        TransactionType = "CHARGE"
	TransactionTypeRefund        TransactionType = "REFUND"
	TransactionTypeVoid          TransactionType = "VOID"
	TransactionTypeChargeback    TransactionType = "CHARGEBACK"
)

type PaymentMethodType string

const (
	PaymentMethodTypeCard        PaymentMethodType = "CARD"
	PaymentMethodTypeBankAccount PaymentMethodType = "BANK_ACCOUNT"
	PaymentMethodTypePayPal      PaymentMethodType = "PAYPAL"
	PaymentMethodTypeApplePay    PaymentMethodType = "APPLE_PAY"
	PaymentMethodTypeGooglePay   PaymentMethodType = "GOOGLE_PAY"
)

type Payment struct {
	ID                     uuid.UUID
	OrderID                string
	CustomerID             string
	Amount                 decimal.Decimal
	Currency               string
	Status                 PaymentStatus
	Gateway                string
	GatewayTransactionID   sql.NullString
	GatewayPaymentIntentID sql.NullString
	PaymentMethodID        uuid.NullUUID
	CardLast4              sql.NullString
	CardBrand              sql.NullString
	Metadata               sql.NullString
	FailureReason          sql.NullString
	FailureCode            sql.NullString
	CreatedAt              time.Time
	ProcessedAt            sql.NullTime
	CompletedAt            sql.NullTime
	UpdatedAt              sql.NullTime
	CorrelationID          sql.NullString
	IdempotencyKey         sql.NullString
}

type PaymentMethod struct {
	ID                      uuid.UUID
	CustomerID              string
	Type                    PaymentMethodType
	Gateway                 string
	GatewayToken            string
	CardLast4               sql.NullString
	CardBrand               sql.NullString
	CardExpMonth            sql.NullString
	CardExpYear             sql.NullString
	CardholderNameEncrypted sql.NullString
	BillingEmail            sql.NullString
	BillingAddressEncrypted sql.NullString
	ExpiryEncrypted         sql.NullString
	IsDefault               bool
	IsActive                bool
	Metadata                sql.NullString
	CreatedAt               time.Time
	UpdatedAt               sql.NullTime
	LastUsedAt              sql.NullTime
}

type Refund struct {
	ID              uuid.UUID
	PaymentID       uuid.UUID
	Amount          decimal.Decimal
	Currency        string
	Status          RefundStatus
	Reason          sql.NullString
	GatewayRefundID sql.NullString
	FailureReason   sql.NullString
	FailureCode     sql.NullString
	CreatedAt       time.Time
	ProcessedAt     sql.NullTime
	CompletedAt     sql.NullTime
	CorrelationID   sql.NullString
	InitiatedBy     sql.NullString
}

type Transaction struct {
	ID                   uuid.UUID
	PaymentID            uuid.UUID
	RefundID             uuid.NullUUID
	Type                 TransactionType
	Amount               decimal.Decimal
	Currency             string
	Success              bool
	GatewayTransactionID sql.NullString
	GatewayResponse      sql.NullString
	GatewayErrorCode     sql.NullString
	GatewayErrorMessage  sql.NullString
	CreatedAt            time.Time
	CorrelationID        sql.NullString
	IPAddress            sql.NullString
	UserAgent            sql.NullString
}
