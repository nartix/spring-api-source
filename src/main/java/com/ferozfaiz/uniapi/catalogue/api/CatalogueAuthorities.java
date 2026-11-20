package com.ferozfaiz.uniapi.catalogue.api;

import java.util.Set;
import org.springframework.security.core.Authentication;

public final class CatalogueAuthorities {

  public static final String SCOPE_CATALOGUE_READ = "SCOPE_catalogue:read";
  public static final String SCOPE_CATALOGUE_WRITE = "SCOPE_catalogue:write";
  public static final String CATALOGUE_READ = "CATALOGUE_READ";
  public static final String CATALOGUE_WRITE = "CATALOGUE_WRITE";
  public static final String SERVICE_CATALOGUE_READ = "SERVICE_CATALOGUE_READ";
  public static final String SERVICE_CATALOGUE_WRITE = "SERVICE_CATALOGUE_WRITE";
  public static final Set<String> CATALOGUE_READ_AUTHORITIES = Set.of(
    SCOPE_CATALOGUE_READ,
    CATALOGUE_READ,
    SERVICE_CATALOGUE_READ
  );
  public static final Set<String> CATALOGUE_WRITE_AUTHORITIES = Set.of(
    SCOPE_CATALOGUE_WRITE,
    CATALOGUE_WRITE,
    SERVICE_CATALOGUE_WRITE
  );
  public static final String CATALOGUE_READ_EXPRESSION =
    "hasAnyAuthority('SCOPE_catalogue:read','CATALOGUE_READ','SERVICE_CATALOGUE_READ')";
  public static final String CATALOGUE_WRITE_EXPRESSION =
    "hasAnyAuthority('SCOPE_catalogue:write','CATALOGUE_WRITE','SERVICE_CATALOGUE_WRITE')";

  private CatalogueAuthorities() {}

  public static boolean hasCatalogueRead(Authentication authentication) {
    return hasAnyAuthority(authentication, CATALOGUE_READ_AUTHORITIES);
  }

  public static boolean hasCatalogueWrite(Authentication authentication) {
    return hasAnyAuthority(authentication, CATALOGUE_WRITE_AUTHORITIES);
  }

  private static boolean hasAnyAuthority(Authentication authentication, Set<String> authorities) {
    return (
      authentication != null &&
      authentication.isAuthenticated() &&
      authentication
        .getAuthorities()
        .stream()
        .anyMatch(authority -> authorities.contains(authority.getAuthority()))
    );
  }
}
