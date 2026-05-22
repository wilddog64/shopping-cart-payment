---
# Copilot Review Findings — PR #19 (fix/oidc-issuer-keycloak)

**Date:** 2026-05-22
**PR:** https://github.com/wilddog64/shopping-cart-payment/pull/19

---

## Finding 1 — `oauth2.jwk-set-uri` should stay in-cluster

**File:** `k8s/base/configmap.yaml` line 14
**Copilot comment ID:** 3287918679

### What Copilot flagged

`oauth2.jwk-set-uri` was changed to the external Keycloak hostname
(`https://keycloak.3ai-talk.org`), but the existing NetworkPolicy only
explicitly allows Keycloak egress on ports 80/8080 within the `identity`
namespace. Using the external URL relies on the broad "allow-to-payment-gateways"
443 egress rule instead.

### Fix applied (commit `011c2f5`)

```yaml
# Before (PR initial commit f0bc971)
  oauth2.jwk-set-uri: "https://keycloak.3ai-talk.org/realms/shopping-cart/protocol/openid-connect/certs"

# After (commit 011c2f5)
  oauth2.jwk-set-uri: "http://keycloak.identity.svc.cluster.local/realms/shopping-cart/protocol/openid-connect/certs"
```

`oauth2.issuer-uri` stays external — it must match the `iss` claim that
Keycloak advertises (`KC_HOSTNAME_URL`). The JWK set URI is only used to
fetch public keys, which the internal service returns identically; no issuer
validation occurs on that call.

### Root cause

The original fix changed both URIs to match the pattern, but they serve
different purposes: `issuer-uri` validates JWT claims, `jwk-set-uri` fetches
keys. Only the former must match `KC_HOSTNAME_URL`.

### Process note

When fixing OIDC issuer mismatches: update `oauth2.issuer-uri` (or
`OAUTH2_ISSUER_URI`) to the external URL; leave `jwk-set-uri` (or equivalent
JWKS endpoint) pointing at the internal Keycloak service.
