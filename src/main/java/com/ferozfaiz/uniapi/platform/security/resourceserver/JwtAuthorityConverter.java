package com.ferozfaiz.uniapi.platform.security.resourceserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

class JwtAuthorityConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private final JwtGrantedAuthoritiesConverter scopeAuthorities =
    new JwtGrantedAuthoritiesConverter();

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopeAuthorities.convert(jwt));
    claimAsStringList(jwt, "roles").forEach(role ->
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
    );
    claimAsStringList(jwt, "permissions").forEach(permission ->
      authorities.add(new SimpleGrantedAuthority(permission))
    );
    return authorities;
  }

  private static Collection<String> claimAsStringList(Jwt jwt, String claimName) {
    Object claim = jwt.getClaims().get(claimName);
    if (claim instanceof Collection<?> values) {
      return values.stream().map(String::valueOf).toList();
    }
    if (claim instanceof String value && !value.isBlank()) {
      return new ArrayList<>(List.of(value));
    }
    return Set.of();
  }
}
