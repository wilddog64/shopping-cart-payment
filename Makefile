# Payment Service Makefile
# Provides convenient commands for building, testing, and running the service

.PHONY: help build test test-unit test-integration test-all clean run docker-build docker-run lint check

# Default target
help:
	@echo "Payment Service - Available Commands"
	@echo "====================================="
	@echo ""
	@echo "Build & Run:"
	@echo "  make build              - Build the application (skip tests)"
	@echo "  make build-with-tests   - Build the application with tests"
	@echo "  make run                - Run the application locally"
	@echo "  make clean              - Clean build artifacts"
	@echo ""
	@echo "Testing:"
	@echo "  make test               - Run all tests (unit + integration)"
	@echo "  make test-unit          - Run unit tests only"
	@echo "  make test-integration   - Run integration tests only (requires Docker)"
	@echo "  make test-coverage      - Run tests with coverage report"
	@echo "  make test-verbose       - Run all tests with verbose output"
	@echo ""
	@echo "Code Quality:"
	@echo "  make lint               - Run code style checks"
	@echo "  make check              - Run all checks (compile + lint + tests)"
	@echo "  make format             - Format code using spotless"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-build       - Build Docker image"
	@echo "  make docker-run         - Run in Docker container"
	@echo "  make docker-test        - Run tests in Docker"
	@echo ""
	@echo "Database:"
	@echo "  make db-migrate         - Run Flyway migrations"
	@echo "  make db-clean           - Clean database (development only)"
	@echo "  make db-info            - Show Flyway migration info"
	@echo ""

# ==============================================================================
# Build targets
# ==============================================================================

build:
	@echo "Building application (skipping tests)..."
	./mvnw clean package -DskipTests

build-with-tests:
	@echo "Building application with tests..."
	./mvnw clean package

clean:
	@echo "Cleaning build artifacts..."
	./mvnw clean
	rm -rf target/

run:
	@echo "Running application..."
	./mvnw spring-boot:run

# ==============================================================================
# Test targets
# ==============================================================================

# Run all tests
test: test-all

test-all:
	@echo "Running all tests..."
	./mvnw test

# Run only unit tests (exclude integration tests)
test-unit:
	@echo "Running unit tests..."
	./mvnw test -Dtest="!*IntegrationTest" -DfailIfNoTests=false

# Run only integration tests (requires Docker for Testcontainers)
test-integration:
	@echo "Running integration tests..."
	@echo "Note: Docker must be running for Testcontainers"
	./mvnw test -Dtest="*IntegrationTest"

# Run tests with coverage report
test-coverage:
	@echo "Running tests with coverage..."
	./mvnw clean verify jacoco:report
	@echo ""
	@echo "Coverage report generated at: target/site/jacoco/index.html"

# Run tests with verbose output
test-verbose:
	@echo "Running all tests (verbose)..."
	./mvnw test -Dsurefire.useFile=false

# Run a specific test class
# Usage: make test-class CLASS=PaymentServiceTest
test-class:
	@echo "Running test class: $(CLASS)"
	./mvnw test -Dtest="$(CLASS)"

# Run tests matching a pattern
# Usage: make test-pattern PATTERN="Payment*"
test-pattern:
	@echo "Running tests matching: $(PATTERN)"
	./mvnw test -Dtest="$(PATTERN)"

# ==============================================================================
# Code Quality targets
# ==============================================================================

lint:
	@echo "Running code style checks..."
	./mvnw checkstyle:check

format:
	@echo "Formatting code..."
	./mvnw spotless:apply

check: lint test-all
	@echo "All checks passed!"

compile:
	@echo "Compiling source code..."
	./mvnw compile

# ==============================================================================
# Docker targets
# ==============================================================================

DOCKER_IMAGE_NAME ?= shopping-cart-payment
DOCKER_IMAGE_TAG ?= latest

docker-build:
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG) .

docker-run:
	@echo "Running Docker container..."
	docker run -p 8083:8083 \
		-e SPRING_PROFILES_ACTIVE=local \
		$(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG)

docker-test:
	@echo "Running tests in Docker..."
	docker run --rm \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-v $(PWD):/app \
		-w /app \
		maven:3.9-eclipse-temurin-21 \
		./mvnw test

docker-push:
	@echo "Pushing Docker image..."
	docker push $(DOCKER_IMAGE_NAME):$(DOCKER_IMAGE_TAG)

# ==============================================================================
# Database targets
# ==============================================================================

db-migrate:
	@echo "Running database migrations..."
	./mvnw flyway:migrate

db-clean:
	@echo "WARNING: This will clean the database!"
	@echo "Press Ctrl+C to cancel, or Enter to continue..."
	@read _
	./mvnw flyway:clean

db-info:
	@echo "Showing Flyway migration info..."
	./mvnw flyway:info

db-repair:
	@echo "Repairing Flyway metadata..."
	./mvnw flyway:repair

# ==============================================================================
# Development helpers
# ==============================================================================

# Start local development environment
dev:
	@echo "Starting development environment..."
	docker-compose up -d postgres
	./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Stop local development environment
dev-stop:
	@echo "Stopping development environment..."
	docker-compose down

# View application logs
logs:
	@echo "Tailing application logs..."
	tail -f logs/application.log

# Generate API documentation
docs:
	@echo "Generating API documentation..."
	./mvnw springdoc-openapi:generate

# ==============================================================================
# CI/CD helpers
# ==============================================================================

ci-test:
	@echo "Running CI test suite..."
	./mvnw clean verify -B

ci-build:
	@echo "Building for CI..."
	./mvnw clean package -DskipTests -B

ci-publish:
	@echo "Publishing artifacts..."
	./mvnw deploy -DskipTests -B

# ==============================================================================
# Utilities
# ==============================================================================

# Show dependency tree
deps:
	./mvnw dependency:tree

# Check for dependency updates
deps-update:
	./mvnw versions:display-dependency-updates

# Show effective POM
pom:
	./mvnw help:effective-pom
