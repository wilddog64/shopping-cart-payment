package payment

import (
	"bytes"
	"encoding/json"
	"testing"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

func TestPaymentResponseAmountMarshalsAsJSONNumber(t *testing.T) {
	resp := PaymentResponse{
		ID:            uuid.Nil,
		Amount:        Amount(decimal.RequireFromString("42.5000")),
		CreatedAt:     nil,
		CompletedAt:   nil,
		CardLast4:     nil,
		CardBrand:     nil,
		FailureReason: nil,
	}

	got, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if !bytes.Contains(got, []byte(`"amount":42.5000`)) {
		t.Fatalf("amount field serialized incorrectly: %s", string(got))
	}
	if bytes.Contains(got, []byte(`"amount":"42.5000"`)) {
		t.Fatalf("amount field is quoted: %s", string(got))
	}
}

func TestRefundResponseAmountMarshalsAsJSONNumber(t *testing.T) {
	resp := RefundResponse{
		ID:        uuid.Nil,
		PaymentID: uuid.Nil,
		Amount:    Amount(decimal.RequireFromString("7.0000")),
	}

	got, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if !bytes.Contains(got, []byte(`"amount":7.0000`)) {
		t.Fatalf("amount field serialized incorrectly: %s", string(got))
	}
	if bytes.Contains(got, []byte(`"amount":"7.0000"`)) {
		t.Fatalf("amount field is quoted: %s", string(got))
	}
}
