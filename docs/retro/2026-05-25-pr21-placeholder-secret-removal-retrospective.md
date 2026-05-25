# Retrospective — PR #21: Remove Placeholder secret.yaml

**Date:** 2026-05-25
**PR:** #21 — merged to main
**Participants:** Claude, Codex, Copilot

## What Went Well
- Root cause correctly identified: ESO owns both secrets via creationPolicy: Owner; git placeholder caused permanent OutOfSync
- Codex applied a clean two-file change (kustomization.yaml + delete secret.yaml)
- Copilot flagged two documentation clarity issues that were valid and worth fixing

## What Went Wrong
- Placeholder secret.yaml was left in git after ESO integration was set up — should have been removed at that time
- CHANGELOG naming was ambiguous (ExternalSecret name vs resulting Secret name)

## Decisions Made
- Secrets provisioned by ESO must never be checked into git alongside ExternalSecret manifests
- kustomization.yaml now has an explicit comment noting ESO ownership to prevent future drift

## Theme
ArgoCD showed shopping-cart-payment as permanently OutOfSync because a placeholder secret.yaml conflicted with ESO's creationPolicy: Owner on the same secrets. Removing the file cleared the drift. Copilot improved documentation clarity around ExternalSecret→Secret name mapping.
