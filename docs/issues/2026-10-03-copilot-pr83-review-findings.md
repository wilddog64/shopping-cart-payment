# Copilot PR #83 review findings

**PR:** #83 — ci: pin build-push-deploy to the never-backwards promote loop
**Date:** 2026-10-03

## Finding 1 — CHANGELOG entry orphaned between `### Fixed` and `### Changed`

**File:** `CHANGELOG.md:24`

Copilot flagged that the new `build-push-deploy.yml` bump entry sat after the blank line that closes
the `### Fixed` list, directly against the `### Changed` heading, so it rendered outside the Fixed
list.

**Fix (`171d8e4`):**

Before:
```
- `go/Dockerfile`: bump the build stage ...

- `.github/workflows/ci.yaml`: bump the `build-push-deploy.yml` reusable workflow ...
### Changed
```

After:
```
- `go/Dockerfile`: bump the build stage ...
- `.github/workflows/ci.yaml`: bump the `build-push-deploy.yml` reusable workflow ...

### Changed
```

**Root cause:** the entry was inserted at the index of `\n### Changed`, which lies after the
section's trailing blank line rather than after its last bullet.

**Process note:** when inserting into a CHANGELOG section programmatically, anchor on the section's
last bullet (or strip trailing blank lines before inserting), not on the next heading.
