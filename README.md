# Payment Service

PCI DSS compliant payment processing microservice for the Shopping Cart platform.

## Overview

The Payment Service handles all payment-related operations including:
- Payment processing through multiple gateways (Stripe, PayPal)
- Payment method tokenization
- Full and partial refunds
- Transaction logging and audit trail

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Payment Service                             │
│                                                                   │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │  Controller │───▶│   Service   │───▶│  Gateway Router     │ │
│  └─────────────┘    └─────────────┘    └─────────────────────┘ │
│         │                  │                      │              │
│         ▼                  ▼                      ▼              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │    DTOs     │    │ Repository  │    │ Stripe │ PayPal │Mock│ │
│  └─────────────┘    └─────────────┘    └─────────────────────┘ │
│                            │                                     │
│                            ▼                                     │
│                     ┌─────────────┐                             │
│                     │ PostgreSQL  │                             │
│                     └─────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

## Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.2 |
| Language | Java 21 |
| Database | PostgreSQL |
| Security | OAuth2/JWT, AES-256-GCM |
| Gateways | Stripe, PayPal, Mock |
| Metrics | Micrometer + Prometheus |
| Build | Maven |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 15+
- Docker (optional)

### Run Locally

```bash
# Clone the repository
git clone https://github.com/your-org/shopping-cart-payment.git
cd shopping-cart-payment

# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=payments
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword

# Build and run
./mvnw spring-boot:run
```

### Run with Docker

```bash
# Build image
docker build -t payment-service:latest .

# Run container
docker run -p 8084:8084 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=payments \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpassword \
  payment-service:latest
```

## API Documentation

See [docs/api/payments.md](docs/api/payments.md) for complete API documentation.

### Quick Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/payments` | POST | Process payment |
| `/api/payments/{id}` | GET | Get payment |
| `/api/payments?orderId={id}` | GET | Get by order |
| `/api/payments/{id}/refund` | POST | Refund payment |
| `/actuator/health` | GET | Health check |

## Configuration

Key configuration properties (see `application.yml`):

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8084 | Service port |
| `payment.gateway.default` | mock | Default gateway |
| `payment.encryption.enabled` | true | Enable PCI encryption |

### Gateway Configuration

**Stripe:**
```yaml
payment:
  gateway:
    stripe:
      enabled: true
      api-key: ${STRIPE_API_KEY}
      webhook-secret: ${STRIPE_WEBHOOK_SECRET}
```

**PayPal:**
```yaml
payment:
  gateway:
    paypal:
      enabled: true
      client-id: ${PAYPAL_CLIENT_ID}
      client-secret: ${PAYPAL_CLIENT_SECRET}
      mode: sandbox  # or 'live'
```

## Testing

See [docs/testing/README.md](docs/testing/README.md) for the complete testing guide.

### Quick Commands

```bash
# Run all tests
make test

# Run unit tests only
make test-unit

# Run integration tests (requires Docker)
make test-integration

# Run tests with coverage
make test-coverage
```

### Test Structure

| Type | Location | Description |
|------|----------|-------------|
| Unit Tests | `src/test/java/.../service/` | Test components in isolation |
| Controller Tests | `src/test/java/.../controller/` | Test REST endpoints with mocks |
| Gateway Tests | `src/test/java/.../gateway/` | Test payment gateway logic |
| Integration Tests | `src/test/java/.../integration/` | Test with real PostgreSQL (Testcontainers) |

### Test Cards (Mock Gateway)

| Card Number | Result |
|-------------|--------|
| 4242424242424242 | Success |
| 4000000000000002 | Declined |
| 4000000000009995 | Insufficient funds |

## Kubernetes Deployment

This service runs in the `shopping-cart-payment` namespace for PCI DSS isolation.

### Health Probes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8084
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8084
```

### Required Secrets

| Secret | Source | Description |
|--------|--------|-------------|
| `payment-db-secret` | Vault | Database credentials |
| `payment-gateway-secrets` | Vault | Stripe/PayPal API keys |
| `payment-encryption-secret` | Vault | AES encryption key |

## Security

### PCI DSS Compliance

- No storage of full card numbers (PAN)
- No storage of CVV/CVC
- AES-256-GCM encryption for sensitive data
- PCI data masking in logs
- Separate namespace isolation
- Network policies restricting traffic

### Authentication

- OAuth2/JWT for API access
- Roles: `PAYMENT_USER`, `PAYMENT_ADMIN`, `PLATFORM_ADMIN`

## Monitoring

### Endpoints

- Health: `GET /actuator/health`
- Metrics: `GET /actuator/prometheus`
- Info: `GET /actuator/info`

### Key Metrics

- `payment_processing_seconds` - Processing latency
- `payment_total` - Payment count by status
- `refund_total` - Refund count by status

## Project Structure

```
src/main/java/com/shoppingcart/payment/
├── controller/        # REST controllers
├── service/           # Business logic
├── entity/            # JPA entities
├── repository/        # Data access
├── gateway/           # Payment gateway integrations
│   ├── mock/
│   ├── stripe/
│   └── paypal/
├── security/          # Encryption, masking
└── dto/               # Request/response objects
```

## Related Services

| Service | Description |
|---------|-------------|
| [Order Service](../shopping-cart-order) | Creates orders, calls payment |
| [Infrastructure](../shopping-cart-infra) | Kubernetes manifests |
| [E2E Tests](../shopping-cart-e2e-tests) | Integration tests |

## License

MIT
