package com.shoppingcart.payment.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        addRoles(jwt.getClaim("realm_access"), roles);
        addResourceRoles(jwt.getClaim("resource_access"), roles);

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalize(role)));
        }
        return authorities;
    }

    private void addResourceRoles(Object resourceAccess, Set<String> roles) {
        if (!(resourceAccess instanceof Map<?, ?> resources)) {
            return;
        }
        for (Object client : resources.values()) {
            addRoles(client, roles);
        }
    }

    private void addRoles(Object access, Set<String> roles) {
        if (!(access instanceof Map<?, ?> accessMap)) {
            return;
        }
        Object roleValues = accessMap.get("roles");
        if (!(roleValues instanceof List<?> roleList)) {
            return;
        }
        for (Object role : roleList) {
            if (role instanceof String stringRole && !stringRole.isBlank()) {
                roles.add(stringRole);
            }
        }
    }

    private String normalize(String role) {
        return role.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
