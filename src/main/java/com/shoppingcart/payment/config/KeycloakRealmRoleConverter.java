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
