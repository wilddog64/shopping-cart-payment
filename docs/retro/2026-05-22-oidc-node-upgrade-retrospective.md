# Retrospective — OIDC Fix + Node.js 20→22 Upgrade

**Date:** 2026-05-22
**PR:** #20 — merged to main (`5edcf02664761571ef8694f8f259777a12c02893`)
**Participants:** Claude, Codex, Copilot

## What Went Well
- Codex committed all 4 repos cleanly with correct commit messages
- Copilot caught missing `@Bean` annotations in rate-limit doc
- NODE_VERSION env var centralization prevents future drift
- Merge conflict in e2e-tests resolved cleanly (both sides removed push/PR triggers)

## What Went Wrong
- shopping-cart-payment had a merge conflict (configmap.yaml + CHANGELOG diverged from main)
- e2e-tests docs/next-improvements branch was cut before PR #2 merged — push/PR triggers regressed
- Retro doc in payment incorrectly claimed configMapGenerator was implemented
- rate-limit issue doc was missing @Bean annotations on all 3 bucket config methods

## Decisions Made
- `jwk-set-uri` stays in-cluster (`http://keycloak.identity.svc.cluster.local/...`); only `issuer-uri` uses external URL
- push/pull_request triggers remain disabled in e2e-tests CI until service containers are added
- npm audit `--force` deferred for frontend (11 remaining dev-only transitive vulns)

## Theme
Completed OIDC issuer URL fix across all shopping-cart services. Keycloak KC_HOSTNAME_STRICT=true requires all OIDC clients to match the external domain as issuer. Simultaneously upgraded Node.js from 20 to 22 across CI workflows and centralized the version via NODE_VERSION env var to prevent drift. Several merge conflicts and branch-cut-order issues required manual resolution.
