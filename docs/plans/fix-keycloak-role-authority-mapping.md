# Bugfix: Map Keycloak roles to Spring authorities (payment 403)

**Repo:** `shopping-cart-payment`
**Branch:** `fix/keycloak-role-authority-mapping` (create from `origin/main`)
**Files:**
- `src/main/java/com/shoppingcart/payment/config/KeycloakRealmRoleConverter.java` (new)
- `src/main/java/com/shoppingcart/payment/config/SecurityConfig.java` (edit)
- `src/test/java/com/shoppingcart/payment/config/KeycloakRealmRoleConverterTest.java` (new)
- `CHANGELOG.md` (edit)

---

## Problem

Every authenticated call to `POST /api/v1/payments` returns **HTTP 403** even with a
valid Keycloak access token. The payment therefore never reaches the Stripe gateway.

**Root cause:** `PaymentController.processPayment` is guarded by
`@PreAuthorize("hasAnyRole('PAYMENT_USER','PAYMENT_WRITE','PAYMENT_ADMIN','PLATFORM_ADMIN')")`,
but `SecurityConfig` wires the resource server with the **stock** converter:

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt())
```

The default `JwtAuthenticationConverter` maps only the `scope`/`scp` claim to `SCOPE_*`
authorities. It never reads Keycloak's `realm_access.roles` or
`resource_access.<client>.roles`. Confirmed by absence — the strings
`JwtAuthenticationConverter`, `realm_access`, and `resource_access` appear **nowhere** in
`src/`. So a fully valid Keycloak JWT yields **zero `ROLE_*` authorities**, `hasAnyRole(...)`
fails, and Spring returns 403 (authenticated-but-unauthorized).

This is the textbook 401→403 transition: once the JWKS URI was corrected so the token
*authenticates*, the missing role mapping surfaced as an *authorization* 403.

---

## Reproduction

1. Obtain a valid access token from the `shopping-cart` realm on `keycloak.3ai-talk.org`
   for a principal that has a `PAYMENT_*` realm role.
2. `POST /api/v1/payments` with `Authorization: Bearer <token>` and a valid body.
3. **Expected:** request reaches `PaymentService` / `StripeGateway`.
   **Actual:** `403 Forbidden` before the controller body runs.

---

## Fix

### Change 1 — new file `KeycloakRealmRoleConverter.java`

```java
package com.shoppingcart.payment.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak realm and client roles from a JWT into Spring Security
 * ROLE_* authorities.
 *
 * The stock JwtAuthenticationConverter only maps the "scope"/"scp" claim to
 * SCOPE_* authorities and never reads Keycloak's realm_access.roles /
 * resource_access.&lt;client&gt;.roles. Without this converter every
 * {@code @PreAuthorize("hasAnyRole(...)")} check fails with 403 even for a
 * valid token.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            addRoles(authorities, realmMap.get("roles"));
        }

        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object client : resourceMap.values()) {
                if (client instanceof Map<?, ?> clientMap) {
                    addRoles(authorities, clientMap.get("roles"));
                }
            }
        }

        return authorities;
    }

    private void addRoles(List<GrantedAuthority> authorities, Object roles) {
        if (roles instanceof Collection<?> roleList) {
            for (Object role : roleList) {
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }
    }
}
```

### Change 2 — `SecurityConfig.java`: wire the converter

**Exact old block (lines 1–29):**

```java
package com.shoppingcart.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt())
                .httpBasic(customizer -> {});

        return http.build();
    }
}
```

**Exact new block:**

```java
package com.shoppingcart.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .httpBasic(customizer -> {});

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
```

> Note: replacing the granted-authorities converter drops the default `SCOPE_*`
> authorities. That is safe — no endpoint in this service authorizes on scopes; every
> `@PreAuthorize` uses `hasAnyRole(...)`.

### Change 3 — new file `KeycloakRealmRoleConverterTest.java`

```java
package com.shoppingcart.payment.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeycloakRealmRoleConverter Tests")
class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user");
    }

    @Test
    @DisplayName("maps realm_access.roles to ROLE_ authorities")
    void mapsRealmRoles() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("PAYMENT_USER", "PLATFORM_ADMIN")))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PAYMENT_USER", "ROLE_PLATFORM_ADMIN");
    }

    @Test
    @DisplayName("maps resource_access client roles to ROLE_ authorities")
    void mapsClientRoles() {
        Jwt jwt = baseJwt()
                .claim("resource_access", Map.of(
                        "payment-service", Map.of("roles", List.of("PAYMENT_WRITE"))))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PAYMENT_WRITE");
    }

    @Test
    @DisplayName("returns empty when no role claims present")
    void emptyWhenNoRoles() {
        Jwt jwt = baseJwt().build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }
}
```

### Change 4 — `CHANGELOG.md`: add under `## [Unreleased]` → `### Fixed`

```
- Map Keycloak `realm_access.roles` / `resource_access.<client>.roles` to Spring Security `ROLE_*` authorities via a `JwtAuthenticationConverter`. The stock resource-server converter only mapped the `scope` claim to `SCOPE_*`, so `@PreAuthorize("hasAnyRole('PAYMENT_USER',...)")` rejected every valid Keycloak token with 403 before the request reached the payment gateway.
```

---

## Files Changed

| File | Change |
|------|--------|
| `config/KeycloakRealmRoleConverter.java` | new — realm + client role → `ROLE_*` |
| `config/SecurityConfig.java` | wire `JwtAuthenticationConverter` bean into `oauth2ResourceServer` |
| `config/KeycloakRealmRoleConverterTest.java` | new — 3 unit tests |
| `CHANGELOG.md` | `### Fixed` entry |

---

## Rules

- `mvn -q -DskipTests=false test` passes; the 3 new converter tests pass.
- No change to `PaymentController`, `StripeGateway`, `application.yml`, or any Go file.
- Minimal patch — do not refactor unrelated code.

---

## Definition of Done

- [ ] `KeycloakRealmRoleConverter` created; `SecurityConfig` wires it via `jwtAuthenticationConverter()` bean
- [ ] `KeycloakRealmRoleConverterTest` created; all 3 tests pass
- [ ] `mvn test` green (full suite, no regressions)
- [ ] CHANGELOG `### Fixed` entry added
- [ ] Committed and pushed to `fix/keycloak-role-authority-mapping`
- [ ] memory-bank updated with commit SHA and task status

**Commit message (exact):**
```
fix(security): map Keycloak realm/client roles to Spring ROLE_ authorities
```

---

## Follow-up (out of scope for this commit — note in report, do NOT implement)

After the converter merges, the live 403 only fully clears if the `shopping-cart` realm
actually grants the calling principal a `PAYMENT_*` role. If a decoded token shows no
`PAYMENT_*` in `realm_access.roles` / `resource_access`, a **second** change is needed in
the Keycloak realm config (assign the role to the user/client), not in this service.

---

## What NOT to Do

- Do NOT create a PR
- Do NOT skip pre-commit / pre-push hooks (`--no-verify`, `ALLOW_MAIN_PUSH=1`)
- Do NOT modify any file other than the four listed targets
- Do NOT commit to `main` — work on `fix/keycloak-role-authority-mapping`
- Do NOT touch the `fix/payment-ghcr-eso-pull-secret` branch or its open PR
