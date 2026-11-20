package com.ferozfaiz.uniapi.data.api;

import java.util.Set;
import org.springframework.security.core.Authentication;

public final class CategoryAuthorities {

  public static final String CATEGORIES_READ = "categories:read";
  public static final String CATEGORIES_WRITE = "categories:write";
  public static final String SCOPE_CATEGORIES_READ = "SCOPE_categories:read";
  public static final String SCOPE_CATEGORIES_WRITE = "SCOPE_categories:write";
  public static final Set<String> CATEGORIES_READ_AUTHORITIES = Set.of(
    CATEGORIES_READ,
    SCOPE_CATEGORIES_READ
  );
  public static final Set<String> CATEGORIES_WRITE_AUTHORITIES = Set.of(
    CATEGORIES_WRITE,
    SCOPE_CATEGORIES_WRITE
  );
  public static final String CATEGORIES_READ_EXPRESSION =
    "hasAnyAuthority('categories:read','SCOPE_categories:read')";
  public static final String CATEGORIES_WRITE_EXPRESSION =
    "hasAnyAuthority('categories:write','SCOPE_categories:write')";

  private CategoryAuthorities() {}

  public static boolean hasCategoriesRead(Authentication authentication) {
    return hasAnyAuthority(authentication, CATEGORIES_READ_AUTHORITIES);
  }

  public static boolean hasCategoriesWrite(Authentication authentication) {
    return hasAnyAuthority(authentication, CATEGORIES_WRITE_AUTHORITIES);
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
