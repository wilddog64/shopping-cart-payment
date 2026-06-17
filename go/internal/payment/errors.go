package payment

import (
	"errors"
	"fmt"
)

type APIError struct {
	Status  int    `json:"-"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

func (e *APIError) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

func BadRequest(code, message string) *APIError {
	return &APIError{Status: 400, Code: code, Message: message}
}

func NotFound(code, message string) *APIError {
	return &APIError{Status: 404, Code: code, Message: message}
}

func Conflict(code, message string) *APIError {
	return &APIError{Status: 409, Code: code, Message: message}
}

var (
	ErrPaymentNotFound        = errors.New("payment not found")
	ErrRefundNotFound         = errors.New("refund not found")
	ErrRefundNotAllowed       = errors.New("refund not allowed")
	ErrRefundExceedsRemaining = errors.New("refund amount exceeds remaining refundable amount")
)

func APIErrorFromErr(err error) *APIError {
	switch {
	case err == nil:
		return nil
	case errors.Is(err, ErrPaymentNotFound):
		return NotFound("PAYMENT_NOT_FOUND", err.Error())
	case errors.Is(err, ErrRefundNotFound):
		return NotFound("REFUND_NOT_FOUND", err.Error())
	case errors.Is(err, ErrRefundNotAllowed):
		return BadRequest("REFUND_NOT_ALLOWED", err.Error())
	case errors.Is(err, ErrRefundExceedsRemaining):
		return BadRequest("REFUND_AMOUNT_TOO_LARGE", err.Error())
	default:
		return &APIError{Status: 500, Code: "INTERNAL_SERVER_ERROR", Message: err.Error()}
	}
}
