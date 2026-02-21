# Tech Context: Payment Service

## Language & Runtime

- **Java 21** — Maven artifact: `com.shoppingcart:payment-service:1.0.0-SNAPSHOT`
- Built and run via Maven wrapper `./mvnw` (`.mvn/wrapper/maven-wrapper.properties` present)

## Framework & Core Dependencies (pom.xml)

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-parent | 3.2.0 | BOM and plugin management |
| spring-boot-starter-web | (managed) | REST API / embedded Tomcat |
| spring-boot-starter-data-jpa | (managed) | Hibernate + Spring Data |
| spring-boot-starter-validation | (managed) | Jakarta Bean Validation |
| spring-boot-starter-actuator | (managed) | Health, metrics, Prometheus |
| spring-boot-starter-security | (managed) | Spring Security |
| spring-boot-starter-oauth2-resource-server | (managed) | JWT validation via Keycloak JWKS |
| org.flywaydb:flyway-core | (managed) | Database schema migrations |
| org.flywaydb:flyway-database-postgresql | (managed) | Flyway PostgreSQL support |
| com.shoppingcart:rabbitmq-client | 1.0.0 | RabbitMQ client with Vault integration |
| com.stripe:stripe-java | 24.0.0 | Stripe payment gateway SDK |
| com.paypal.sdk:checkout-sdk | 1.14.0 | PayPal payment gateway SDK |
| com.bucket4j:bucket4j-core | 8.7.0 | Rate limiting |
| com.github.ben-manes.caffeine:caffeine | (managed) | Rate limit bucket cache |
| io.micrometer:micrometer-registry-prometheus | (managed) | Prometheus export |
| org.bouncycastle:bcprov-jdk18on | 1.77 | AES-256-GCM encryption (PCI DSS) |
| org.projectlombok:lombok | (managed) | `@Slf4j`, `@Builder`, `@RequiredArgsConstructor` |

## Test Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-test | (managed) | JUnit 5, Mockito, AssertJ |
| spring-security-test | (managed) | Security context mocking |
| com.h2database:h2 | (managed) | In-memory DB for unit tests |
| org.testcontainers:testcontainers | 1.19.3 | Container management |
| org.testcontainers:postgresql | 1.19.3 | Real PostgreSQL container |
| org.testcontainers:rabbitmq | 1.19.3 | Real RabbitMQ container |

## Database Schema

Managed by Flyway. Migration: `src/main/resources/db/migration/V1__init_schema.sql`

Tables:
- `payments` — primary payment records; unique index on `idempotency_key`; indexes on order_id, customer_id, status, created_at
- `refunds` — refunds linked to payments (FK)
- `transactions` — immutable audit log for all gateway interactions
- `payment_methods` — tokenized payment methods; encrypted fields for cardholder_name and billing_address

## Infrastructure Dependencies

| Service | Required | Default Address | Purpose |
|---|---|---|---|
| PostgreSQL 15+ | Yes | localhost:5432/payments | Persistent storage |
| RabbitMQ 3.12+ | Optional | rabbitmq.shopping-cart-data... | Event publishing (RabbitMQ client in pom) |
| Keycloak | Optional | — | JWT issuer / JWKS |
| HashiCorp Vault | Optional | vault.vault.svc.cluster.local:8200 | Gateway API keys, encryption key, DB credentials |
| Stripe API | Optional | api.stripe.com | Live/sandbox payment processing |
| PayPal API | Optional | api.paypal.com | Live/sandbox payment processing |

## Development Environment Setup

### Prerequisites

- Java 21 JDK
- PostgreSQL 15+ running locally or via Docker
- Docker (for integration tests with Testcontainers)
- Optional: Stripe/PayPal test account keys

### Local Setup

```bash
# Start PostgreSQL
docker run -d -p 5432:5432 -e POSTGRES_DB=payments -e POSTGRES_PASSWORD=changeme123 postgres:15

# Run with mock gateway (no real keys needed)
export DB_HOST=localhost
export DB_PASSWORD=changeme123
export PAYMENT_GATEWAY_DEFAULT=mock
./mvnw spring-boot:run
```

### Test Configuration Files

- `src/test/resources/application-test.yml` — unit test configuration (H2)
- `src/test/resources/application-integration-test.yml` — integration test configuration (Testcontainers)

## Build Tooling

- **Maven wrapper** (`./mvnw`) — always use this, not system `mvn`
- **Makefile** — developer-friendly wrapper; `make help` for all targets
- **Dockerfile** — multi-stage: `maven:3.9-eclipse-temurin-21` builder → `eclipse-temurin:21-jre-alpine` runner; non-root user
- **.dockerignore** — present

## CI/CD

Three GitHub Actions workflows:
- `.github/workflows/ci.yaml` — main CI: build, test, security scan, Docker build, deploy
- `.github/workflows/pr-validation.yaml` — PR checks
- `.github/workflows/release.yaml` — versioned releases

## Kubernetes Deployment

- Manifests in `k8s/base/` with Kustomize
- **Namespace**: `shopping-cart-payment` (separate from other services — PCI DSS isolation)
- Resources: deployment.yaml, service.yaml (ClusterIP:8084), configmap.yaml, serviceaccount.yaml, networkpolicy.yaml, hpa.yaml (2–10 replicas), kustomization.yaml
- NetworkPolicy restricts ingress/egress for PCI compliance
- Required secrets (from Vault ExternalSecrets): `payment-db-secret`, `payment-gateway-secrets`, `payment-encryption-secret`
- Liveness: `/actuator/health/liveness`, Readiness: `/actuator/health/readiness`
