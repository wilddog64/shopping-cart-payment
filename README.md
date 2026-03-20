# Payment Service

A PCI-scope Spring Boot service that processes payments, issues refunds, and integrates with Stripe/PayPal. It stores transactions in PostgreSQL, encrypts sensitive data with AES-256-GCM, and runs in the dedicated `shopping-cart-payment` namespace for isolation.

---

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 15+
- Docker (for Testcontainers) and RabbitMQ if running end-to-end

### Install & Run
```bash
./mvnw clean package

# Local run (requires env vars)
./mvnw spring-boot:run

# Docker build/run
docker build -t shopping-cart-payment:latest .
docker run -p 8084:8084 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=payments \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  shopping-cart-payment:latest
```

### Environment Variables
| Variable | Description |
|----------|-------------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `PAYMENT_GATEWAY_DEFAULT` | `mock`, `stripe`, or `paypal` |
| `STRIPE_*`, `PAYPAL_*` | Gateway credentials (from Vault secrets) |
| `ENCRYPTION_ENABLED` | Toggle AES encryption (`true` by default) |
| `PAYMENT_ENCRYPTION_KEY` | 32-byte key for AES-256-GCM |

---

## Usage

### Architecture
```
Controllers → Services → GatewayRouter → Stripe/PayPal/Mock
                          ↘ Repository → PostgreSQL
                          ↘ EncryptionService → AES-256-GCM
```

### API Highlights
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/payments` | POST | Process payment |
| `/api/payments/{id}` | GET | Retrieve payment |
| `/api/payments/{id}/refund` | POST | Refund payment |
| `/actuator/health` | GET | Health probe |

### Testing Commands
```bash
make test         # all tests
make test-unit    # unit tests only
make test-integration  # requires Docker/Testcontainers
make test-coverage
```

### Kubernetes Notes
- Namespace: `shopping-cart-payment` (PCI isolation)
- Secrets required: `payment-db-credentials`, `payment-gateway-secrets` (optional), `payment-encryption-secret`
- Probes target `/actuator/health/liveness` and `/actuator/health/readiness`

---

## Architecture
See **[Service Architecture](docs/architecture/README.md)** for namespace isolation, gateway abstraction, and Vault/ESO integration details.

---

## Directory Layout
```
src/main/java/com/shoppingcart/payment/
├── controller/   # REST controllers
├── service/      # Business logic
├── gateway/      # Stripe/PayPal/Mock adapters
├── repository/   # Data access
├── security/     # Encryption & masking
└── dto/          # Request/response objects
src/test/java/    # Unit + integration tests
k8s/              # Deployment manifests
```

---

## Documentation

### Architecture
- **[Service Architecture](docs/architecture/README.md)** — PCI scope, secrets, gateway router, deployment.

### API Reference
- **[API Reference](docs/api/README.md)** — Endpoint payloads, examples, and gateway responses.

### Testing
- **[Testing Guide](docs/testing/README.md)** — Maven/make commands, suite breakdown, coverage, gateway mocks.

### Troubleshooting
- **[Troubleshooting Guide](docs/troubleshooting/README.md)** — GitHub Packages auth, namespace isolation, Vault/ESO sync, encryption tips.

### Issue Logs
- **[Local Java version mismatch](docs/issues/2026-03-14-local-java-version-mismatch.md)** — CI instructions for Java alignment.
- **[CI Maven wrapper/GitHub Packages fix](docs/issues/2026-03-17-ci-maven-wrapper-fix.md)** — Dockerfile + secret setup for package downloads.

---

## Releases

| Version | Date | Highlights |
|---------|------|------------|
| v0.1.1 | 2026-03-20 | Add missing k8s Secrets; fix pr-validation CI; reduce to single replica + remove HPA for dev/test |
| v0.1.0 | 2026-03-14 | Initial payment processing release with Stripe/PayPal gateways |

---

## Related
- [Platform Architecture](https://github.com/wilddog64/shopping-cart-infra/blob/main/docs/architecture.md)
- [shopping-cart-infra](https://github.com/wilddog64/shopping-cart-infra)
- [shopping-cart-order](https://github.com/wilddog64/shopping-cart-order)
- [shopping-cart-product-catalog](https://github.com/wilddog64/shopping-cart-product-catalog)
- [shopping-cart-basket](https://github.com/wilddog64/shopping-cart-basket)

---

## License
Apache 2.0
