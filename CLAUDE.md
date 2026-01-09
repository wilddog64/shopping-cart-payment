# CLAUDE.md - Payment Service

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**Payment Service** is a PCI DSS compliant payment processing microservice for the Shopping Cart platform. It handles payment processing, refunds, and payment method tokenization through multiple gateway providers.

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 21
- **Database**: PostgreSQL
- **Message Queue**: RabbitMQ (with Vault credential integration)
- **Payment Gateways**: Stripe, PayPal, Mock (for testing)
- **Security**: OAuth2/JWT, AES-256-GCM encryption
- **Build**: Maven

## Repository Structure

```
shopping-cart-payment/
├── src/main/java/com/shoppingcart/payment/
│   ├── PaymentServiceApplication.java    # Main application
│   ├── controller/
│   │   └── PaymentController.java        # REST API endpoints
│   ├── service/
│   │   ├── PaymentService.java           # Payment processing logic
│   │   └── RefundService.java            # Refund processing logic
│   ├── entity/
│   │   ├── Payment.java                  # Payment entity
│   │   ├── Refund.java                   # Refund entity
│   │   ├── PaymentMethod.java            # Tokenized payment methods
│   │   ├── Transaction.java              # Transaction log
│   │   ├── PaymentStatus.java            # Payment status enum
│   │   ├── RefundStatus.java             # Refund status enum
│   │   ├── PaymentMethodType.java        # Payment method types
│   │   └── TransactionType.java          # Transaction types
│   ├── repository/
│   │   ├── PaymentRepository.java
│   │   ├── RefundRepository.java
│   │   ├── PaymentMethodRepository.java
│   │   └── TransactionRepository.java
│   ├── gateway/
│   │   ├── PaymentGateway.java           # Gateway interface
│   │   ├── PaymentGatewayRouter.java     # Gateway selection
│   │   ├── PaymentRequest.java           # Gateway request DTO
│   │   ├── PaymentResult.java            # Gateway result DTO
│   │   ├── RefundRequest.java
│   │   ├── RefundResult.java
│   │   ├── TokenizeRequest.java
│   │   ├── TokenizeResult.java
│   │   ├── mock/
│   │   │   └── MockGateway.java          # Test gateway
│   │   ├── stripe/
│   │   │   └── StripeGateway.java        # Stripe integration
│   │   └── paypal/
│   │       └── PayPalGateway.java        # PayPal integration
│   ├── security/
│   │   ├── EncryptionService.java        # AES-256-GCM encryption
│   │   └── PciDataMasker.java            # PCI data masking
│   └── dto/
│       ├── ProcessPaymentRequest.java
│       ├── PaymentResponse.java
│       └── RefundRequest.java
├── src/main/resources/
│   └── application.yml                   # Configuration
├── pom.xml                               # Maven dependencies
└── CLAUDE.md                             # This file
```

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `POST /api/payments` | POST | PAYMENT_USER | Process a payment |
| `GET /api/payments/{id}` | GET | PAYMENT_USER | Get payment by ID |
| `GET /api/payments?orderId={id}` | GET | PAYMENT_USER | Get payment by order |
| `GET /api/payments?customerId={id}` | GET | PAYMENT_USER | Get customer payments |
| `POST /api/payments/{id}/refund` | POST | PAYMENT_ADMIN | Refund a payment |
| `GET /actuator/health` | GET | Public | Health check |
| `GET /actuator/prometheus` | GET | Public | Metrics |

## Configuration

Key configuration in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8084 | Service port |
| `payment.gateway.default` | mock | Default gateway |
| `payment.gateway.stripe.enabled` | true | Enable Stripe |
| `payment.gateway.paypal.enabled` | true | Enable PayPal |
| `payment.encryption.enabled` | true | Enable PCI encryption |
| `rate-limit.requests-per-minute` | 60 | Rate limiting |

## Payment Flow

```
1. Client → POST /api/payments
2. PaymentController validates request
3. PaymentService checks idempotency
4. PaymentService creates Payment record (PENDING)
5. PaymentGatewayRouter selects gateway
6. Gateway processes payment
7. Transaction logged
8. Payment status updated (COMPLETED/FAILED)
9. Response returned to client
```

## Implementation Status

### Completed

- [x] Core entities (Payment, Refund, PaymentMethod, Transaction)
- [x] JPA repositories with custom queries
- [x] PaymentService with idempotency handling
- [x] RefundService with partial refund support
- [x] PaymentController with security annotations
- [x] MockGateway with test card support
- [x] StripeGateway with PaymentIntent API
- [x] PayPalGateway implementation
- [x] PaymentGatewayRouter for gateway selection
- [x] EncryptionService (AES-256-GCM)
- [x] PciDataMasker for log sanitization
- [x] OAuth2 resource server configuration
- [x] Actuator health endpoints
- [x] Prometheus metrics endpoint
- [x] Rate limiting configuration
- [x] RabbitMQ client dependency

### Infrastructure Completed

- [x] Database migrations (Flyway) - `V1__init_schema.sql`
- [x] Dockerfile (multi-stage, JRE Alpine, non-root user)
- [x] Maven wrapper for reproducible builds
- [x] Kubernetes manifests in `k8s/base/`:
  - [x] Deployment with security context and probes
  - [x] Service (ClusterIP:8084)
  - [x] ConfigMap for configuration
  - [x] ServiceAccount
  - [x] NetworkPolicy (PCI DSS isolation)
  - [x] HorizontalPodAutoscaler (2-10 replicas)
  - [x] Kustomization
- [x] Vault ExternalSecrets (DB, gateways, encryption key)
- [x] GitHub Actions CI/CD workflows:
  - [x] ci.yaml (build, test, security scan, Docker, deploy)
  - [x] pr-validation.yaml (PR checks)
  - [x] release.yaml (versioned releases)
- [x] E2E test integration (PaymentClient, payment flows)

### Testing Completed

- [x] Unit tests:
  - [x] PaymentServiceTest - payment processing, idempotency
  - [x] RefundServiceTest - full/partial refunds, validation
  - [x] PaymentControllerTest - API endpoints, security
  - [x] MockGatewayTest - test cards, tokenization
  - [x] EncryptionServiceTest - AES-256-GCM encrypt/decrypt
  - [x] PaymentGatewayRouterTest - gateway routing
- [x] Test configuration (application-test.yml)

### Pending

- [ ] Integration tests with Testcontainers

## Kubernetes Deployment Plan

### Namespace: `shopping-cart-payment`

Separate namespace for PCI DSS compliance:
- Isolated network policies
- Dedicated RBAC
- Separate secret management
- Independent resource quotas

### Required Infrastructure

1. **Namespace Definition** (`shopping-cart-infra/namespaces/`)
   ```yaml
   apiVersion: v1
   kind: Namespace
   metadata:
     name: shopping-cart-payment
     labels:
       app.kubernetes.io/name: payment-service
       app.kubernetes.io/part-of: shopping-cart
       pci-scope: "true"
   ```

2. **PostgreSQL Database** (`shopping-cart-infra/data-layer/postgresql/payment/`)
   - StatefulSet
   - Service
   - PVC
   - init-db.sql

3. **Vault Secrets**
   - `secret/data/payment/stripe` - Stripe API keys
   - `secret/data/payment/paypal` - PayPal credentials
   - `secret/data/payment/encryption` - AES encryption key
   - `database/creds/payment-readwrite` - Dynamic DB credentials

4. **ExternalSecrets** (`shopping-cart-infra/data-layer/secrets/`)
   - `payment-gateway-secrets`
   - `payment-db-secret`
   - `payment-encryption-secret`

5. **Application Manifests** (this repo or Helm chart)
   - Deployment
   - Service
   - ConfigMap
   - NetworkPolicy
   - ServiceAccount
   - HorizontalPodAutoscaler

### Network Policies

Restrict traffic to/from payment namespace:
- Ingress: Only from `shopping-cart-apps` (order service)
- Egress: Only to payment gateways (Stripe, PayPal APIs)
- Egress: Only to `shopping-cart-data` (PostgreSQL, RabbitMQ)

## E2E Test Integration

Add to `shopping-cart-e2e-tests`:

1. **Update config** (`tests/helpers/api-client.ts`):
   ```typescript
   export const config = {
     // ... existing
     paymentUrl: process.env.PAYMENT_URL || 'http://localhost:8084',
   }
   ```

2. **Create PaymentClient**:
   ```typescript
   export class PaymentClient {
     constructor(private baseUrl: string, private request: APIRequestContext) {}

     async processPayment(data: ProcessPaymentRequest): Promise<Payment> { ... }
     async getPayment(id: string): Promise<Payment> { ... }
     async refundPayment(id: string, amount: number): Promise<Refund> { ... }
   }
   ```

3. **Update checkout flow** (`tests/flows/checkout-flow.spec.ts`):
   - After order creation, call payment service
   - Verify payment status
   - Test refund scenarios

## Common Commands

```bash
# Build
./mvnw clean package

# Run locally
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
./mvnw test

# Build Docker image
docker build -t payment-service:latest .

# Run with Docker
docker run -p 8084:8084 payment-service:latest
```

## Test Cards (MockGateway)

| Card Number | Result |
|-------------|--------|
| 4242424242424242 | Success |
| 4000000000000002 | Declined |
| 4000000000009995 | Insufficient funds |

## Security Notes

1. **PCI DSS Compliance**
   - Never store full card numbers (PAN)
   - Never store CVV
   - Only store tokenized payment methods
   - Encrypt sensitive data at rest
   - Mask PCI data in logs

2. **Authentication**
   - OAuth2/JWT for API access
   - Role-based access (PAYMENT_USER, PAYMENT_ADMIN)
   - Service-to-service auth via JWT

3. **Secrets Management**
   - Gateway API keys in Vault
   - Encryption keys in Vault
   - Database credentials via Vault dynamic secrets

## Related Repositories

- [shopping-cart-infra](../shopping-cart-infra) - Kubernetes infrastructure
- [shopping-cart-order](../shopping-cart-order) - Order service (calls payment)
- [shopping-cart-e2e-tests](../shopping-cart-e2e-tests) - E2E test suite

## Next Steps

1. Create database migration scripts
2. Create Dockerfile
3. Add namespace to infrastructure repo
4. Create Kubernetes manifests
5. Configure Vault secrets
6. Implement E2E test integration
7. Set up CI/CD pipeline
