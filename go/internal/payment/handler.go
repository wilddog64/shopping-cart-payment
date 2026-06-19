package payment

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

type Handler struct {
	paymentService *PaymentService
	refundService  *RefundService
}

func NewHandler(paymentService *PaymentService, refundService *RefundService) *Handler {
	return &Handler{paymentService: paymentService, refundService: refundService}
}

func (h *Handler) Register(r gin.IRoutes) {
	r.POST("/api/v1/payments", h.processPayment)
	r.GET("/api/v1/payments/:paymentId", h.getPayment)
	r.GET("/api/v1/payments", h.getPayments)
	r.GET("/api/v1/payments/order/:orderId", h.getPaymentByOrder)
	r.GET("/api/v1/payments/customer/:customerId", h.getPaymentsByCustomer)
	r.GET("/api/v1/payments/:paymentId/refunds", h.getRefundsForPayment)
	r.POST("/api/v1/payments/:paymentId/refund", h.refundPayment)
}

func (h *Handler) processPayment(c *gin.Context) {
	var req ProcessPaymentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.abortWithError(c, BadRequest("INVALID_REQUEST", err.Error()))
		return
	}
	if err := validateProcessPaymentRequest(req); err != nil {
		h.abortWithError(c, err)
		return
	}

	correlationID := c.GetHeader("X-Correlation-ID")
	idempotencyKey := c.GetHeader("X-Idempotency-Key")
	if strings.TrimSpace(idempotencyKey) == "" {
		idempotencyKey = c.GetHeader("Idempotency-Key")
	}
	if strings.TrimSpace(idempotencyKey) == "" {
		idempotencyKey = req.IdempotencyKey
	}

	payment, err := h.paymentService.ProcessPayment(c.Request.Context(), req, correlationID, idempotencyKey)
	if err != nil {
		h.abortWithError(c, APIErrorFromErr(err))
		return
	}

	status := http.StatusAccepted
	if payment.Status == PaymentStatusCompleted {
		status = http.StatusCreated
	}
	if strings.TrimSpace(correlationID) != "" {
		c.Header("X-Correlation-ID", correlationID)
	}
	c.JSON(status, PaymentResponseFrom(payment))
}

func (h *Handler) getPayment(c *gin.Context) {
	paymentID, err := uuid.Parse(c.Param("paymentId"))
	if err != nil {
		h.abortWithError(c, BadRequest("INVALID_PAYMENT_ID", "paymentId must be a UUID"))
		return
	}
	payment, err := h.paymentService.GetPayment(c.Request.Context(), paymentID)
	if err != nil {
		h.abortWithError(c, APIErrorFromErr(err))
		return
	}
	c.JSON(http.StatusOK, PaymentResponseFrom(payment))
}

func (h *Handler) getPayments(c *gin.Context) {
	orderID := strings.TrimSpace(c.Query("orderId"))
	customerID := strings.TrimSpace(c.Query("customerId"))
	switch {
	case orderID != "":
		payment, err := h.paymentService.GetPaymentByOrderID(c.Request.Context(), orderID)
		if err != nil {
			if err == ErrPaymentNotFound {
				c.JSON(http.StatusOK, []PaymentResponse{})
				return
			}
			h.abortWithError(c, APIErrorFromErr(err))
			return
		}
		c.JSON(http.StatusOK, []PaymentResponse{PaymentResponseFrom(payment)})
	case customerID != "":
		payments, err := h.paymentService.GetPaymentsByCustomer(c.Request.Context(), customerID)
		if err != nil {
			h.abortWithError(c, APIErrorFromErr(err))
			return
		}
		responses := make([]PaymentResponse, 0, len(payments))
		for _, payment := range payments {
			responses = append(responses, PaymentResponseFrom(payment))
		}
		c.JSON(http.StatusOK, responses)
	default:
		h.abortWithError(c, BadRequest("MISSING_QUERY_PARAMS", "orderId or customerId is required"))
	}
}

func (h *Handler) getPaymentByOrder(c *gin.Context) {
	orderID := strings.TrimSpace(c.Param("orderId"))
	payment, err := h.paymentService.GetPaymentByOrderID(c.Request.Context(), orderID)
	if err != nil {
		h.abortWithError(c, APIErrorFromErr(err))
		return
	}
	c.JSON(http.StatusOK, PaymentResponseFrom(payment))
}

func (h *Handler) getPaymentsByCustomer(c *gin.Context) {
	customerID := strings.TrimSpace(c.Param("customerId"))
	payments, err := h.paymentService.GetPaymentsByCustomer(c.Request.Context(), customerID)
	if err != nil {
		h.abortWithError(c, APIErrorFromErr(err))
		return
	}
	responses := make([]PaymentResponse, 0, len(payments))
	for _, payment := range payments {
		responses = append(responses, PaymentResponseFrom(payment))
	}
	c.JSON(http.StatusOK, responses)
}

func (h *Handler) getRefundsForPayment(c *gin.Context) {
	paymentID, err := uuid.Parse(c.Param("paymentId"))
	if err != nil {
		h.abortWithError(c, BadRequest("INVALID_PAYMENT_ID", "paymentId must be a UUID"))
		return
	}
	refunds, err := h.refundService.GetRefundsByPayment(c.Request.Context(), paymentID)
	if err != nil {
		h.abortWithError(c, APIErrorFromErr(err))
		return
	}
	responses := make([]RefundResponse, 0, len(refunds))
	for _, refund := range refunds {
		responses = append(responses, RefundResponseFrom(refund))
	}
	c.JSON(http.StatusOK, responses)
}

func (h *Handler) refundPayment(c *gin.Context) {
	paymentID, err := uuid.Parse(c.Param("paymentId"))
	if err != nil {
		h.abortWithError(c, BadRequest("INVALID_PAYMENT_ID", "paymentId must be a UUID"))
		return
	}
	var req RefundRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.abortWithError(c, BadRequest("INVALID_REQUEST", err.Error()))
		return
	}
	if err := validateRefundRequest(req); err != nil {
		h.abortWithError(c, err)
		return
	}

	refund, err := h.refundService.ProcessRefund(
		c.Request.Context(),
		paymentID,
		req.Amount,
		req.Reason,
		c.GetHeader("X-User-ID"),
		c.GetHeader("X-Correlation-ID"),
	)
	if err != nil {
		h.abortWithError(c, APIErrorFromErr(err))
		return
	}
	c.JSON(http.StatusOK, RefundResponseFrom(refund))
}

func (h *Handler) abortWithError(c *gin.Context, apiErr *APIError) {
	if apiErr == nil {
		apiErr = &APIError{Status: http.StatusInternalServerError, Code: "INTERNAL_SERVER_ERROR", Message: "unknown error"}
	}
	if apiErr.cause != nil {
		_ = c.Error(apiErr.cause)
	}
	c.AbortWithStatusJSON(apiErr.Status, gin.H{
		"code":    apiErr.Code,
		"message": apiErr.Message,
	})
}

func validateProcessPaymentRequest(req ProcessPaymentRequest) *APIError {
	switch {
	case strings.TrimSpace(req.OrderID) == "":
		return BadRequest("ORDER_ID_REQUIRED", "Order ID is required")
	case strings.TrimSpace(req.CustomerID) == "":
		return BadRequest("CUSTOMER_ID_REQUIRED", "Customer ID is required")
	case !decimalCmpPositive(req.Amount):
		return BadRequest("AMOUNT_INVALID", "Amount must be greater than 0")
	case req.Amount.Exponent() < -4:
		return BadRequest("AMOUNT_SCALE_INVALID", "Amount supports at most 4 decimal places")
	case len(strings.TrimSpace(req.Currency)) != 3:
		return BadRequest("CURRENCY_INVALID", "Currency must be 3 characters")
	default:
		return nil
	}
}

func validateRefundRequest(req RefundRequest) *APIError {
	if !decimalCmpPositive(req.Amount) {
		return BadRequest("AMOUNT_INVALID", "Amount must be greater than 0")
	}
	if req.Amount.Exponent() < -4 {
		return BadRequest("AMOUNT_SCALE_INVALID", "Amount supports at most 4 decimal places")
	}
	return nil
}
