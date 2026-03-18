# Issue: Multi-arch workflow pin update

**Date:** 2026-03-17
**Status:** Closed

## Summary
`.github/workflows/ci.yaml` pinned to infra SHA `8363caf`, yielding amd64-only images. Need to reference SHA `999f8d7` that adds multi-arch platforms so arm64 nodes can run pods.

## Fix
- Updated the reusable workflow reference to `build-push-deploy.yml@999f8d70277b92d928412ff694852b05044dbb75`.
- CI will now push amd64 and arm64 images to ghcr.io.

## Follow Up
- Monitor CI + ArgoCD sync for payment namespace once new images publish.
