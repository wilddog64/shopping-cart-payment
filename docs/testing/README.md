# Testing Guide

This document provides a comprehensive guide to testing the Payment Service.

## Table of Contents

- [Overview](#overview)
- [Test Structure](#test-structure)
- [Running Tests](#running-tests)
- [Unit Tests](#unit-tests)
- [Integration Tests](#integration-tests)
- [Test Configuration](#test-configuration)
- [Writing New Tests](#writing-new-tests)
- [Best Practices](#best-practices)

## Overview

The Payment Service uses a multi-layered testing strategy:

| Test Type | Purpose | Tools | Database |
|-----------|---------|-------|----------|
| Unit Tests | Test individual components in isolation | JUnit 5, Mockito | None (mocked) |
| Integration Tests | Test component interactions with real dependencies | JUnit 5, Testcontainers | PostgreSQL |
| E2E Tests | Test full API workflows | Playwright (separate repo) | Real cluster |

## Test Structure

```
src/test/
├── java/com/shoppingcart/payment/
│   ├── controller/           # Controller unit tests
│   │   └── PaymentControllerTest.java
│   ├── service/              # Service unit tests
│   │   ├── PaymentServiceTest.java
│   │   └── RefundServiceTest.java
│   ├── gateway/              # Gateway unit tests
│   │   ├── mock/
│   │   │   └── MockGatewayTest.java
│   │   └── PaymentGatewayRouterTest.java
│   ├── security/             # Security unit tests
│   │   └── EncryptionServiceTest.java
│   └── integration/          # Integration tests
│       ├── BaseIntegrationTest.java
│       ├── PaymentServiceIntegrationTest.java
│       ├── RefundServiceIntegrationTest.java
│       └── PaymentControllerIntegrationTest.java
└── resources/
    ├── application-test.yml           # Unit test config (H2)
    └── application-integration-test.yml  # Integration test config
```

## Running Tests

### Using Make (Recommended)

```bash
# Run all tests
make test

# Run only unit tests
make test-unit

# Run only integration tests (requires Docker)
make test-integration

# Run tests with coverage report
make test-coverage

# Run a specific test class
make test-class CLASS=PaymentServiceTest

# Run tests matching a pattern
make test-pattern PATTERN="*Gateway*"
```

### Using Maven Directly

```bash
# Run all tests
./mvnw test

# Run unit tests only
./mvnw test -Dtest="!*IntegrationTest"

# Run integration tests only
./mvnw test -Dtest="*IntegrationTest"

# Run specific test class
./mvnw test -Dtest=PaymentServiceTest

# Run with verbose output
./mvnw test -Dsurefire.useFile=false
```

## Unit Tests

Unit tests verify individual components in isolation using mocks for dependencies.

### Service Tests

Located in `src/test/java/com/shoppingcart/payment/service/`

**PaymentServiceTest** tests:
- Payment processing with valid data
- Idempotency key handling
- Duplicate order prevention
- Currency normalization
- Payment retrieval by order/customer
- Status updates

**RefundServiceTest** tests:
- Full refund processing
- Partial refund processing
- Multiple partial refunds
- Refund validation (exceeding amount)
- Refund for non-existent payment
- Idempotency handling

### Controller Tests

Located in `src/test/java/com/shoppingcart/payment/controller/`

**PaymentControllerTest** tests:
- POST /api/v1/payments (create)
- GET /api/v1/payments/{id} (get by ID)
- GET /api/v1/payments/order/{orderId}
- GET /api/v1/payments/customer/{customerId}
- POST /api/v1/payments/{id}/refund
- Input validation
- Authorization (roles)

### Gateway Tests

Located in `src/test/java/com/shoppingcart/payment/gateway/`

**MockGatewayTest** tests:
- Successful payment processing
- Test card handling (success/failure)
- Configurable failure rates
- Refund processing
- Gateway metadata

**PaymentGatewayRouterTest** tests:
- Gateway registration
- Gateway retrieval by name
- Default gateway fallback
- Enabled/disabled gateway handling
- Case-sensitive name matching

### Security Tests

Located in `src/test/java/com/shoppingcart/payment/security/`

**EncryptionServiceTest** tests:
- Card data encryption
- Decryption with same key
- Different ciphertext for same plaintext
- Invalid key handling
- Disabled encryption mode

## Integration Tests

Integration tests verify component interactions with real dependencies using Testcontainers.

### Prerequisites

- **Docker** must be running for Testcontainers
- Sufficient system resources for container startup

### Base Test Class

All integration tests extend `BaseIntegrationTest`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payments_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    // Dynamic property configuration...
}
```

### PaymentServiceIntegrationTest

Tests payment service with real database:
- Payment persistence
- Transaction record creation
- Idempotency with database
- Duplicate order prevention
- Payment retrieval (by order, by customer)
- Status updates
- Currency handling

### RefundServiceIntegrationTest

Tests refund service with real database:
- Full refund processing
- Partial refund processing
- Multiple partial refunds
- Refund amount validation
- Refund for non-existent payment
- Idempotency handling

### PaymentControllerIntegrationTest

Tests full API with real database:
- Complete REST endpoint testing
- Request/response validation
- Security (authentication, authorization)
- Correlation ID handling
- Error responses

## Test Configuration

### Unit Test Configuration (application-test.yml)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false

payment:
  gateway:
    default: mock
    mock:
      enabled: true
  encryption:
    enabled: false  # Disable for unit tests
```

### Integration Test Configuration (application-integration-test.yml)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

payment:
  gateway:
    default: mock
    mock:
      enabled: true
      delay-ms: 0
      failure-rate: 0.0
  encryption:
    enabled: true
    key: dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcyE=
```

## Writing New Tests

### Unit Test Template

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("ComponentName Tests")
class ComponentNameTest {

    @Mock
    private DependencyA dependencyA;

    @InjectMocks
    private ComponentName component;

    @Nested
    @DisplayName("methodName")
    class MethodName {

        @Test
        @DisplayName("should do something when condition")
        void shouldDoSomethingWhenCondition() {
            // Arrange
            when(dependencyA.method()).thenReturn(value);

            // Act
            var result = component.methodName();

            // Assert
            assertThat(result).isEqualTo(expected);
            verify(dependencyA).method();
        }
    }
}
```

### Integration Test Template

```java
@DisplayName("ComponentName Integration Tests")
class ComponentNameIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ComponentName component;

    @Autowired
    private Repository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should persist data to database")
    void shouldPersistDataToDatabase() {
        // Arrange
        var input = createTestInput();

        // Act
        var result = component.process(input);

        // Assert
        var persisted = repository.findById(result.getId());
        assertThat(persisted).isPresent();
    }
}
```

## Best Practices

### General

1. **Use descriptive test names** - Test names should describe the behavior being tested
2. **Follow AAA pattern** - Arrange, Act, Assert
3. **One assertion per concept** - Test one thing at a time
4. **Use nested classes** - Group related tests together

### Unit Tests

1. **Mock external dependencies** - Don't hit real databases or APIs
2. **Test edge cases** - Null values, empty collections, boundary conditions
3. **Verify interactions** - Ensure dependencies are called correctly

### Integration Tests

1. **Clean up between tests** - Use `@BeforeEach` to reset state
2. **Use unique identifiers** - Generate UUIDs to avoid conflicts
3. **Test real scenarios** - Focus on end-to-end workflows
4. **Check persistence** - Verify data is actually saved

### Performance

1. **Use container reuse** - `withReuse(true)` for faster test runs
2. **Run unit tests frequently** - Fast feedback loop
3. **Run integration tests before commits** - Catch issues early

## Coverage

Generate coverage reports:

```bash
make test-coverage
```

View report at: `target/site/jacoco/index.html`

### Coverage Goals

| Package | Target |
|---------|--------|
| Service | 80%+ |
| Controller | 75%+ |
| Gateway | 80%+ |
| Security | 90%+ |

## Troubleshooting

### Docker Not Running

```
Could not find a valid Docker environment
```

**Solution**: Start Docker daemon

### Port Already In Use

```
Port 5432 is already in use
```

**Solution**: Testcontainers uses random ports, check for conflicting containers

### Slow Tests

**Solution**:
- Enable container reuse in `~/.testcontainers.properties`:
  ```
  testcontainers.reuse.enable=true
  ```
- Run unit tests separately from integration tests

### Test Flakiness

**Solution**:
- Add appropriate wait conditions
- Use unique test data (UUIDs)
- Clean up state in `@BeforeEach`
