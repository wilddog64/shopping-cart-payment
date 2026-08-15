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
