# Project Brief: Payment Service

## What This Project Does

The Payment Service is a PCI DSS compliant Java/Spring Boot microservice that handles all payment-related operations for the Shopping Cart platform. It processes payments through multiple gateway providers (Stripe, PayPal), manages tokenized payment methods, issues refunds, and maintains a comprehensive transaction audit log.

## Core Responsibilities

- **Payment processing**: Accept payment requests, route to the appropriate payment gateway (Stripe, PayPal, or Mock for testing), record the result, and return a payment response
- **Idempotency**: Prevent duplicate charges by checking idempotency keys before processing
- **Refund management**: Full and partial refunds for completed payments, with audit logging
- **Payment method tokenization**: Store tokenized (not raw) payment methods per customer for reuse
- **Transaction audit log**: Immutable record of every gateway interaction for compliance and debugging
- **PCI DSS compliance**: Enforce data security standards — no raw card storage, AES-256-GCM encryption for sensitive fields, PCI data masking in logs

## Goals

- Provide a secure, compliant payment processing service meeting PCI DSS requirements
- Support multiple payment gateways with a clean routing abstraction
- Enable idempotent payment operations to prevent double-charges
- Maintain full audit trail for all gateway interactions
- Run in an isolated Kubernetes namespace with strict network policies

## Scope

**In scope:**
- Payment processing via Stripe, PayPal, and Mock gateways
- Full and partial refunds
- Tokenized payment method management
- Transaction audit logging (charges, refunds)
- PCI DSS compliant data handling (AES-256-GCM encryption, masking, no raw card storage)
- OAuth2/JWT authentication with role-based access (PAYMENT_USER, PAYMENT_ADMIN, PLATFORM_ADMIN)
- Flyway database migrations
- Prometheus metrics (payment_processing_seconds, payment_total, refund_total)
- Rate limiting
- Kubernetes deployment in isolated `shopping-cart-payment` namespace with NetworkPolicy

**Out of scope:**
- Order management (shopping-cart-order service)
- Cart management (shopping-cart-basket service)
- Product catalog (shopping-cart-product-catalog service)
- Subscription/recurring payment handling
- Webhook processing for async gateway callbacks (planned future work)
- 3D Secure / Strong Customer Authentication flows

## Service Context in the Platform

The Payment Service is called by the Order Service after an order is created. It processes the charge and, upon success, the Order Service updates its status to PAID. Refunds can be initiated by admin users via direct API calls. The service runs in its own dedicated PCI-scoped namespace for compliance isolation.

## Status

**Essentially complete** — all core infrastructure, business logic, tests (unit + integration), Kubernetes manifests, CI/CD pipelines, and documentation are in place. The only explicitly pending item is load/performance testing.
