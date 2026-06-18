package payment

import (
	"bytes"
	"encoding/json"

	"github.com/shopspring/decimal"
)

// Amount serializes monetary values as unquoted JSON numbers.
// It preserves the fixed scale contract used by the Java service.
type Amount decimal.Decimal

func (a Amount) MarshalJSON() ([]byte, error) {
	return []byte(decimal.Decimal(a).StringFixed(4)), nil
}

func (a *Amount) UnmarshalJSON(data []byte) error {
	var value decimal.Decimal
	if err := value.UnmarshalJSON(bytes.TrimSpace(data)); err != nil {
		return err
	}
	*a = Amount(value)
	return nil
}

func (a Amount) Decimal() decimal.Decimal {
	return decimal.Decimal(a)
}

func (a Amount) String() string {
	return decimal.Decimal(a).StringFixed(4)
}

var _ json.Marshaler = Amount{}
