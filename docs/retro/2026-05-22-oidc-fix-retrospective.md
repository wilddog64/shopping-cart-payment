# Retrospective — OIDC Issuer Fix

**Date:** 2026-05-22
**PR:** #19 — merged to main (`e49be1bb956cc5d584d107fbcad46be4da837636`)
**Participants:** Claude, Copilot

## What Went Well
- Copilot caught the missing pod rollout trigger (configmap checksum)
- Copilot caught the JWK set URI NetworkPolicy mismatch (payment)
- configMapGenerator implemented for automatic rolling restarts

## What Went Wrong
- Initial fix set both oauth2.issuer-uri and jwk-set-uri to external URL; only issuer-uri needs to be external
- shopping-cart-payment fix was accidentally pushed directly to main via a branch with wrong tracking ref; required a revert

## Process Notes
- When fixing OIDC issuer mismatches: issuer-uri must match KC_HOSTNAME_URL (external); jwk-set-uri should stay internal
- ConfigMap changes with envFrom do not trigger pod restarts — use configMapGenerator or checksum annotation

## Theme
Fixed Keycloak OIDC issuer URL mismatch in payment service. KC_HOSTNAME_STRICT=true means Keycloak always advertises the external domain as issuer; all OIDC clients must match. Also improved rollout automation via Kustomize configMapGenerator.
