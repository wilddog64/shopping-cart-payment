package com.shoppingcart.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeycloakGrantedAuthoritiesConverterTest {

    private final KeycloakGrantedAuthoritiesConverter converter = new KeycloakGrantedAuthoritiesConverter("payment-service");

    @Test
    void convertsRealmRoles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("PAYMENT_USER", "payment-write"))));

        assertEquals(Set.of("ROLE_PAYMENT_USER", "ROLE_PAYMENT_WRITE"), authorityNames(jwt));
    }

    @Test
    void convertsResourceRoles() {
        Jwt jwt = jwt(Map.of("resource_access", Map.of(
                "payment-service", Map.of("roles", List.of("PAYMENT_READ")))));

        assertEquals(Set.of("ROLE_PAYMENT_READ"), authorityNames(jwt));
    }

    @Test
    void ignoresRolesFromOtherClients() {
        Jwt jwt = jwt(Map.of("resource_access", Map.of(
                "other-client", Map.of("roles", List.of("PAYMENT_ADMIN")),
                "payment-service", Map.of("roles", List.of("PAYMENT_READ")))));

        assertEquals(Set.of("ROLE_PAYMENT_READ"), authorityNames(jwt));
    }

    @Test
    void blankResourceClientIdGrantsOnlyRealmRoles() {
        KeycloakGrantedAuthoritiesConverter blankClientConverter =
                new KeycloakGrantedAuthoritiesConverter("");
        Jwt jwt = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("PAYMENT_USER")),
                "resource_access", Map.of("payment-service", Map.of("roles", List.of("PAYMENT_READ")))));

        Set<String> authorities = blankClientConverter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_PAYMENT_USER"), authorities);
    }

    @Test
    void ignoresMissingAndMalformedAccessClaims() {
        Jwt jwt = jwt(Map.of(
                "realm_access", "not-a-map",
                "resource_access", List.of("not-a-map")));

        assertEquals(Set.of(), authorityNames(jwt));
    }

    @Test
    void groupsDoNotGrantRoles() {
        Jwt jwt = jwt(Map.of("groups", List.of("/PAYMENT_ADMIN")));

        assertEquals(Set.of(), authorityNames(jwt));
    }

    private Set<String> authorityNames(Jwt jwt) {
        return converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(300),
                Map.of("alg", "none"), claims);
    }
}
