# Issue: Payment Service Has No Rate Limiting

**Date:** 2026-03-18
**Status:** Open

## Problem

The payment service has no rate limiting. It is the most sensitive service in the
stack — processing card charges and refunds. An attacker who bypasses the order-service
AuthorizationPolicy (or reaches payment directly) can flood the payment gateway,
exhaust the Stripe/PayPal API quota, or trigger fraud detection lockouts.

Even internally, a buggy order-service retry loop could generate unbounded payment
requests.

## Fix: Add Bucket4j rate limiting with strict limits

Payment requires stricter limits than order/basket:
- POST `/api/payments` (charge) — 5 req/s per IP, 20 req/min
- POST `/api/payments/*/refund` — 2 req/s per IP, 10 req/min
- GET endpoints — 30 req/s per IP (read-only, less critical)

Use Redis-backed Bucket4j (same pattern as order service fix) — cluster-wide enforcement.

### 1. Add dependencies to `pom.xml`

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>3.27.2</version>
</dependency>
```

### 2. Add `RateLimitConfig.java`

```java
package com.shoppingcart.payment.config;

@Configuration
public class RateLimitConfig {

    // Strict: payment processing
    public BucketConfiguration paymentBucketConfig() {
        return BucketConfiguration.builder()
            .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofSeconds(1))))
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
            .build();
    }

    // Stricter: refunds
    public BucketConfiguration refundBucketConfig() {
        return BucketConfiguration.builder()
            .addLimit(Bandwidth.classic(2, Refill.greedy(2, Duration.ofSeconds(1))))
            .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
            .build();
    }

    // Relaxed: read endpoints
    public BucketConfiguration readBucketConfig() {
        return BucketConfiguration.builder()
            .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofSeconds(1))))
            .build();
    }
}
```

### 3. Add `RateLimitFilter.java`

Route-aware filter: inspect `request.getMethod()` + `request.getRequestURI()` to select
the correct bucket configuration. Skip `/actuator/**` health endpoints.

### 4. Register `RedissonClient` bean

Same as order service — use `REDIS_HOST` / `REDIS_PORT` env vars.
Note: payment service connects to `orders-cache` Redis (not basket Redis) — confirm
namespace/service name in k8s manifests before wiring.

## Definition of Done

- [ ] `bucket4j-core`, `bucket4j-redis`, `redisson` added to `pom.xml`
- [ ] `RateLimitConfig` with 3 bucket configs (payment, refund, read)
- [ ] `RateLimitFilter` selecting config by route
- [ ] Returns 429 with `Retry-After` header on limit exceeded
- [ ] Skips `/actuator/**`
- [ ] Unit tests: payment bucket, refund bucket, health bypass
- [ ] No changes to Dockerfiles, k8s manifests, or gateway code

## What NOT to Do

- Do NOT change payment gateway logic (Stripe/PayPal)
- Do NOT block `order-service` ServiceAccount from internal calls — rate limit applies to
  IP, not service identity (Istio AuthzPolicy handles identity)
- Do NOT merge without confirming which Redis instance payment should use
