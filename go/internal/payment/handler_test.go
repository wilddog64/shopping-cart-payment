package payment

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"

	"github.com/wilddog64/shopping-cart-payment/go/internal/gateway"
)

func TestHandlerErrorMapping(t *testing.T) {
	gin.SetMode(gin.TestMode)

	t.Run("unknown payment is 404 with code/message", func(t *testing.T) {
		handler := newTestHandler(t, newFakeStore())
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/payments/"+uuid.NewString(), nil)
		handler.engine.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Fatalf("status = %d, want %d", rec.Code, http.StatusNotFound)
		}
		assertErrorJSONKeys(t, rec.Body.Bytes())
	})

	t.Run("invalid UUID is 400 with code/message", func(t *testing.T) {
		handler := newTestHandler(t, newFakeStore())
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/payments/not-a-uuid", nil)
		handler.engine.ServeHTTP(rec, req)

		if rec.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
		}
		assertErrorJSONKeys(t, rec.Body.Bytes())
	})

	t.Run("validation and over-refund are 400 with code/message", func(t *testing.T) {
		store := newFakeStore()
		payment := seedCompletedPayment(store, "10.0000")
		handler := newTestHandler(t, store)

		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/api/v1/payments", bytes.NewBufferString(`{"orderId":"","customerId":"customer","amount":1,"currency":"USD"}`))
		req.Header.Set("Content-Type", "application/json")
		handler.engine.ServeHTTP(rec, req)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
		}
		assertErrorJSONKeys(t, rec.Body.Bytes())

		rec = httptest.NewRecorder()
		body := bytes.NewBufferString(`{"amount":20,"reason":"too much"}`)
		req = httptest.NewRequest(http.MethodPost, "/api/v1/payments/"+payment.ID.String()+"/refund", body)
		req.Header.Set("Content-Type", "application/json")
		handler.engine.ServeHTTP(rec, req)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("refund status = %d, want %d", rec.Code, http.StatusBadRequest)
		}
		assertErrorJSONKeys(t, rec.Body.Bytes())
	})
}

type testHandler struct {
	engine *gin.Engine
}

func newTestHandler(t *testing.T, store *fakeStore) *testHandler {
	t.Helper()
	router := gateway.NewRouter("mock", gateway.NewMockGateway(true, 0, 0))
	paymentService := NewPaymentService(store, router)
	refundService := NewRefundService(store, router)
	handler := NewHandler(paymentService, refundService)

	engine := gin.New()
	handler.Register(engine)
	return &testHandler{engine: engine}
}

func assertErrorJSONKeys(t *testing.T, body []byte) {
	t.Helper()
	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("unmarshal error body: %v", err)
	}
	if _, ok := payload["code"]; !ok {
		t.Fatalf("error body missing code: %s", string(body))
	}
	if _, ok := payload["message"]; !ok {
		t.Fatalf("error body missing message: %s", string(body))
	}
}
