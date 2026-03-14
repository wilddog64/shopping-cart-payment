# Issue: Local Java Version Mismatch (OpenJDK 25 vs pom.xml Java 21)

**Date:** 2026-03-14
**Severity:** Local dev only — CI is NOT affected
**Status:** Open — workaround available

---

## Symptoms

`mvn test` or `mvn verify` hangs or times out locally when Codex runs integration tests
during the `fix/ci-stabilization` work.

## Root Cause

Local machine has **OpenJDK 25.0.2** (installed via Homebrew 2026-03-13):

```
/opt/homebrew/Cellar/openjdk/25.0.2
```

`pom.xml` targets **Java 21**:

```xml
<java.version>21</java.version>
```

Java 25 is forward-compatible for compilation, but Testcontainers + Spring Boot 3.2
integration tests can exhibit timeout and module-system issues on JVM versions newer
than the target — particularly with reflection access and container startup timing.

## CI Impact

**None.** GitHub Actions CI workflow pins Java explicitly:

```yaml
env:
  JAVA_VERSION: '21'
```

CI uses Java 21 and is unaffected. Codex should push changes and let CI validate
rather than running `mvn verify` locally.

## Fix

Install OpenJDK 21 and pin it for this project:

```bash
brew install openjdk@21
```

Pin via `.envrc` (direnv):
```bash
# in shopping-cart-payment directory
echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk@21' >> .envrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> .envrc
direnv allow
```

Or use `jenv`:
```bash
brew install jenv
jenv add /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
# in project dir:
jenv local 21
```

## Workaround (immediate)

Codex should skip local `mvn verify` and rely on CI:
1. Fix the compilation errors in source
2. Push to `fix/ci-stabilization`
3. Monitor `gh run list --repo wilddog64/shopping-cart-payment --branch fix/ci-stabilization`
4. CI (Java 21) is the source of truth for test results
