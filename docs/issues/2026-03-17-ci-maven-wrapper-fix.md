# CI Failure: Maven Wrapper Incompatible with JDK 21 Alpine

**Date:** 2026-03-17
**Severity:** CI blocking — Docker build fails, image never pushed to ghcr.io
**Status:** Open — assigned to Codex

---

## Symptoms

`Build, Scan & Push` and `Build Docker Image` jobs fail in CI with:

```
process "/bin/sh -c ./mvnw dependency:go-offline -B" did not complete successfully: exit code: 1
maven.multiModuleProjectDirectory system property is not set
```

## Root Cause

The Maven Wrapper (`./mvnw`) is incompatible with JDK 21 Alpine (`eclipse-temurin:21-jdk-alpine`).
The `maven.multiModuleProjectDirectory` system property is not set by the wrapper script in this
environment, causing immediate exit with code 1.

## Fix

Replace Maven Wrapper with a direct `apk add maven` install in `Dockerfile` and ensure Maven has
credentials for the GitHub Packages repo.

**In `Dockerfile` — Stage 1 (builder):**

1. Remove the lines that copy the Maven Wrapper:
   ```dockerfile
   COPY .mvn/ .mvn/
   COPY mvnw pom.xml ./
   ```
   Replace with:
   ```dockerfile
   RUN apk add --no-cache maven
   COPY pom.xml checkstyle.xml ./
   ```

2. Replace Maven Wrapper invocations:
   ```dockerfile
   # Before
   RUN ./mvnw dependency:go-offline -B
   ...
   RUN ./mvnw package -DskipTests -B

   # After
   RUN --mount=type=secret,id=GH_TOKEN \
       mkdir -p /root/.m2 && \
       printf '<settings>\n  <servers>\n    <server>\n      <id>github-rabbitmq-client</id>\n      <username>x-token-auth</username>\n      <password>%s</password>\n    </server>\n  </servers>\n</settings>\n' "$(cat /run/secrets/GH_TOKEN)" > /root/.m2/settings.xml && \
       mvn dependency:go-offline -B
   ...
   RUN --mount=type=secret,id=GH_TOKEN mvn package -DskipTests -B
   ```

## Constraints

- Only touch `Dockerfile` — do NOT modify `pom.xml`, `ci.yaml`, or infra workflow
- Do NOT use `--no-verify` on commits
- Commit on `main` branch

## Verification

```bash
gh run list -R wilddog64/shopping-cart-payment --limit 1 --json status,conclusion,headSha
gh api repos/wilddog64/shopping-cart-payment/packages/container/shopping-cart-payment/versions \
  --jq '.[0] | {version: .name, updated: .updated_at}'
```

## Status

- Fix applied on `main` (commit `377cdf4`) — waiting on CI `Run 23175303688` to finish Security Scan.
- Older queued workflows cancelled to reduce load.
