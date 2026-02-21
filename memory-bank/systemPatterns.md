# System Patterns: Payment Service

## Architectural Pattern: Layered + Gateway Strategy Pattern

```
HTTP Client (Order Service or Admin)
    │
    ▼ (OAuth2 JWT auth + rate limiting)
┌─────────────────────────────────────┐
│  PaymentController                   │
│  - @PreAuthorize role checks         │
│  - DTO validation                    │
└────────────────┬────────────────────┘
                 │
         ┌───────┴────────┐
         ▼                ▼
┌─────────────┐  ┌─────────────┐
│ PaymentSvc  │  │  RefundSvc  │
│ (idempotent)│  │ (partial ok)│
└──────┬──────┘  └──────┬──────┘
       │                │
       ▼                ▼
┌─────────────────────────────────────┐
│  PaymentGatewayRouter               │
│  getGatewayOrDefault(name)          │
└────────┬─────────┬──────────────────┘
         │         │
    ┌────┘    ┌────┘
    ▼         ▼         ▼
Stripe    PayPal      Mock
Gateway   Gateway    Gateway
         │
         ▼ (all)
┌─────────────────────────────────────┐
│  Repositories                        │
│  Payment, Refund, Transaction,       │
│  PaymentMethod                       │
└──────────────┬──────────────────────┘
               │
               ▼
         PostgreSQL
```

## Domain Model

### Payment Entity (primary record)
```
id: UUID
orderId: String
customerId: String
amount: BigDecimal (10,4)
currency: String (3)
status: PaymentStatus
gateway: String
gatewayTransactionId: String
gatewayPaymentIntentId: String
paymentMethodId: UUID (FK to payment_methods)
cardLast4: String (safe to store per PCI DSS)
cardBrand: String
idempotencyKey: String (unique index)
correlationId: String
createdAt, processedAt, completedAt, updatedAt: Timestamp
failureCode, failureReason: String
```

### PaymentStatus Enum
```
PENDING → PROCESSING → COMPLETED
                   └→ FAILED
COMPLETED → REFUND_PENDING → REFUNDED
                          └→ REFUND_FAILED
```

### Refund Entity
```
id: UUID
paymentId: UUID (FK)
amount: BigDecimal (partial or full)
currency: String
status: RefundStatus (PENDING, PROCESSING, COMPLETED, FAILED)
reason: String
gatewayRefundId: String
initiatedBy: String
createdAt, processedAt, completedAt: Timestamp
```

### Transaction Entity (audit log — immutable)
```
id: UUID
paymentId: UUID (FK)
refundId: UUID (optional FK)
type: TransactionType (CHARGE, REFUND, TOKENIZE)
amount: BigDecimal
success: Boolean
gatewayTransactionId: String
gatewayResponse: Text (raw gateway JSON)
gatewayErrorCode, gatewayErrorMessage: String
createdAt: Timestamp
correlationId: String
```

### PaymentMethod Entity (tokenized, PCI-safe)
```
id: UUID
customerId: String
type: PaymentMethodType (CREDIT_CARD, DEBIT_CARD, PAYPAL, etc.)
gateway: String
gatewayToken: String (the tokenized reference)
cardLast4: String
cardBrand: String
cardExpMonth, cardExpYear: String
cardholderNameEncrypted: Text (AES-256-GCM)
billingAddressEncrypted: Text (AES-256-GCM)
isDefault: Boolean
isActive: Boolean
lastUsedAt: Timestamp
```

## Gateway Strategy Pattern

### PaymentGateway Interface
```java
interface PaymentGateway {
    String getName();                                    // "stripe", "paypal", "mock"
    PaymentResult processPayment(PaymentRequest req);
    RefundResult processRefund(RefundRequest req);
    TokenizeResult tokenize(TokenizeRequest req);
}
```

### PaymentGatewayRouter
Selects gateway by name with fallback to default:
```java
gateway = router.getGatewayOrDefault(requestedGateway);
// Falls back to router.getDefaultGateway() if name is null/unknown
// Default configured via payment.gateway.default (default: "mock")
```

Implementations:
- `StripeGateway` — uses Stripe PaymentIntent API
- `PayPalGateway` — uses PayPal Checkout SDK
- `MockGateway` — test implementation with configurable delay and failure rate; test cards: 4242... (success), 4000...0002 (declined), 4000...9995 (insufficient funds)

## Idempotency Pattern

Payment processing is idempotent via `idempotencyKey`:
```java
// 1. Check idempotency key — return existing if found
Optional<Payment> existing = repo.findByIdempotencyKey(key);
if (existing.isPresent()) return existing.get();

// 2. Check order already paid
Optional<Payment> orderPayment = repo.findByOrderId(orderId);
if (orderPayment.isPresent() && orderPayment.get().getStatus() == COMPLETED)
    return orderPayment.get();

// 3. Create new payment and process
```

## PCI DSS Security Patterns

### EncryptionService
AES-256-GCM encryption/decryption for sensitive fields:
```java
String encrypted = encryptionService.encrypt(plaintextValue);
String decrypted = encryptionService.decrypt(encryptedValue);
```
Key sourced from `ENCRYPTION_KEY` env var (ultimately from Vault).

### PciDataMasker
Sanitizes log output to strip card numbers, CVV, and other PCI-sensitive data. Applied before any logging of payment-related objects.

### Data Rules
- Full PAN (Primary Account Number): **never stored**
- CVV/CVC: **never stored**
- Card numbers in logs: **masked** by PciDataMasker
- Cardholder name: stored **AES-256-GCM encrypted** in payment_methods
- Card last4 + brand: **safe to store** (per PCI DSS SAQ D)
- Gateway tokens: stored in `gateway_token` column (not raw card data)

## Authentication & Authorization

### Roles
- `PAYMENT_USER` — process payments, view own payments
- `PAYMENT_ADMIN` — process payments, view payments, issue refunds
- `PLATFORM_ADMIN` — full access

### Spring Security Setup
- OAuth2 Resource Server: JWT validated via Keycloak JWKS
- `@PreAuthorize` annotations on controller methods enforce role requirements
- Rate limiting via `RateLimitFilter` (Bucket4j, per-IP, 10 RPS / burst 20 in default config)

## Flyway Migration Pattern

Migrations in `src/main/resources/db/migration/` following `V{n}__{Description}.sql` convention:
- `V1__init_schema.sql` — creates all 4 tables with indexes
- Future migrations increment version number

Flyway config:
- `baseline-on-migrate: true` — handles existing databases
- `validate-on-migrate: true` — validates checksums on startup

## Observability Patterns

- **Logging**: SLF4J `@Slf4j`; PciDataMasker applied before logging payment data; correlation_id included in all transaction records
- **Metrics** (Prometheus): `payment_processing_seconds`, `payment_total{status=...}`, `refund_total{status=...}`
- **Health**: `/actuator/health/liveness` and `/actuator/health/readiness` (Spring Boot 3.x probes enabled)
- **Graceful shutdown**: `server.shutdown: graceful` in application.yml
- **HikariCP connection pool**: 10 max / 2 min connections; timeouts configured

## Network Isolation (Kubernetes)

The `networkpolicy.yaml` in k8s/base/ enforces:
- **Ingress**: only from `shopping-cart-apps` namespace (Order Service calls)
- **Egress**: only to payment gateway APIs (Stripe, PayPal), `shopping-cart-data` namespace (PostgreSQL, RabbitMQ), Vault
- All other traffic blocked — enforces PCI DSS network segmentation requirement
