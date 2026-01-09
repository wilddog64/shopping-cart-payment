# Payment Service API

Base URL: `http://localhost:8084/api`

## Authentication

All endpoints require OAuth2/JWT authentication with appropriate roles:
- `PAYMENT_USER`: Can process and view payments
- `PAYMENT_ADMIN`: Can process payments and issue refunds
- `PLATFORM_ADMIN`: Full access

Include the JWT token in the Authorization header:
```
Authorization: Bearer <token>
```

## Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes | Bearer token |
| `Content-Type` | Yes | `application/json` |
| `X-Correlation-ID` | No | Request correlation ID for tracing |
| `X-Idempotency-Key` | No | Idempotency key to prevent duplicates |
| `X-User-ID` | No | User ID for refund requests |

---

## Endpoints

### Process Payment

Process a payment for an order.

```
POST /payments
```

**Request Body:**

```json
{
  "orderId": "order-123",
  "customerId": "customer-456",
  "amount": 99.99,
  "currency": "USD",
  "gateway": "stripe",
  "paymentMethodId": "pm_xxx",
  "idempotencyKey": "unique-key-123"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `orderId` | string | Yes | Order ID |
| `customerId` | string | Yes | Customer ID |
| `amount` | decimal | Yes | Payment amount (> 0.01) |
| `currency` | string | Yes | 3-letter currency code (USD, EUR, etc.) |
| `gateway` | string | No | Gateway name (stripe, paypal, mock). Defaults to configured default |
| `paymentMethodId` | string | No | Tokenized payment method ID |
| `cardNumber` | string | No | Card number (for direct processing) |
| `cardExpMonth` | string | No | Card expiration month (MM) |
| `cardExpYear` | string | No | Card expiration year (YYYY) |
| `cardCvc` | string | No | Card CVC |
| `cardholderName` | string | No | Cardholder name |
| `idempotencyKey` | string | No | Unique key for idempotent requests |

**Response (201 Created / 202 Accepted):**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderId": "order-123",
  "customerId": "customer-456",
  "amount": 99.99,
  "currency": "USD",
  "status": "COMPLETED",
  "gateway": "stripe",
  "gatewayTransactionId": "ch_xxx",
  "cardLast4": "4242",
  "cardBrand": "visa",
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:01Z"
}
```

**Status Codes:**
- `201 Created`: Payment completed successfully
- `202 Accepted`: Payment processing (async)
- `400 Bad Request`: Invalid request
- `401 Unauthorized`: Missing/invalid token
- `403 Forbidden`: Insufficient permissions
- `409 Conflict`: Duplicate payment for order

---

### Get Payment by ID

Retrieve a payment by its ID.

```
GET /payments/{paymentId}
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `paymentId` | UUID | Payment ID |

**Response (200 OK):**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderId": "order-123",
  "customerId": "customer-456",
  "amount": 99.99,
  "currency": "USD",
  "status": "COMPLETED",
  "gateway": "stripe",
  "gatewayTransactionId": "ch_xxx",
  "cardLast4": "4242",
  "cardBrand": "visa",
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:01Z"
}
```

**Status Codes:**
- `200 OK`: Payment found
- `404 Not Found`: Payment not found

---

### Get Payment by Order ID

Retrieve a payment for a specific order.

```
GET /payments?orderId={orderId}
```

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `orderId` | string | Order ID |

**Response (200 OK):**

```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "orderId": "order-123",
    "customerId": "customer-456",
    "amount": 99.99,
    "currency": "USD",
    "status": "COMPLETED",
    ...
  }
]
```

---

### Get Payments by Customer

Retrieve all payments for a customer.

```
GET /payments?customerId={customerId}
```

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | string | Customer ID |

**Response (200 OK):**

```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "orderId": "order-123",
    ...
  },
  {
    "id": "b2c3d4e5-f6a7-8901-bcde-f23456789012",
    "orderId": "order-456",
    ...
  }
]
```

---

### Refund Payment

Issue a full or partial refund for a payment.

```
POST /payments/{paymentId}/refund
```

**Required Role:** `PAYMENT_ADMIN` or `PLATFORM_ADMIN`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `paymentId` | UUID | Payment ID to refund |

**Request Body:**

```json
{
  "amount": 49.99,
  "reason": "Customer request"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `amount` | decimal | Yes | Refund amount (<= original payment) |
| `reason` | string | No | Refund reason |

**Response (201 Created):**

```json
{
  "id": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "paymentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 49.99,
  "currency": "USD",
  "status": "COMPLETED",
  "reason": "Customer request",
  "gatewayRefundId": "re_xxx",
  "createdAt": "2024-01-15T11:00:00Z",
  "completedAt": "2024-01-15T11:00:01Z"
}
```

**Status Codes:**
- `201 Created`: Refund processed
- `400 Bad Request`: Invalid amount or payment status
- `404 Not Found`: Payment not found

---

## Payment Status Values

| Status | Description |
|--------|-------------|
| `PENDING` | Payment created, not yet processed |
| `PROCESSING` | Payment being processed by gateway |
| `COMPLETED` | Payment successful |
| `FAILED` | Payment failed |
| `REFUND_PENDING` | Refund in progress |
| `REFUNDED` | Fully refunded |
| `REFUND_FAILED` | Refund failed |

---

## Refund Status Values

| Status | Description |
|--------|-------------|
| `PENDING` | Refund created |
| `PROCESSING` | Refund being processed |
| `COMPLETED` | Refund successful |
| `FAILED` | Refund failed |

---

## Error Response

All errors return a standard error response:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Amount must be greater than 0",
  "path": "/api/payments"
}
```

---

## Test Cards (Mock Gateway)

When using `gateway: "mock"`:

| Card Number | Result |
|-------------|--------|
| `4242424242424242` | Success |
| `4000000000000002` | Card declined |
| `4000000000009995` | Insufficient funds |

---

## Health Check

```
GET /actuator/health
```

**Response:**

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## Metrics

Prometheus metrics endpoint:

```
GET /actuator/prometheus
```

Key metrics:
- `payment_processing_seconds` - Payment processing latency
- `payment_total` - Total payments by status
- `refund_total` - Total refunds by status
