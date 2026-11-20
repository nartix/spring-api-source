package com.ferozfaiz.uniapi.iam.api;

import java.util.Set;
import org.springframework.security.core.Authentication;

public final class UserAuthorities {

  public static final String ROLE_ADMIN = "ROLE_ADMIN";
  public static final String USERS_READ = "users:read";
  public static final String USERS_WRITE = "users:write";
  public static final String SCOPE_USERS_READ = "SCOPE_users:read";
  public static final String SCOPE_USERS_WRITE = "SCOPE_users:write";
  public static final Set<String> USERS_READ_AUTHORITIES = Set.of(
    ROLE_ADMIN,
    USERS_READ,
    SCOPE_USERS_READ
  );
  public static final Set<String> USERS_WRITE_AUTHORITIES = Set.of(
    ROLE_ADMIN,
    USERS_WRITE,
    SCOPE_USERS_WRITE
  );
  public static final String USERS_READ_EXPRESSION =
    "hasAnyAuthority('ROLE_ADMIN','users:read','SCOPE_users:read')";
  public static final String USERS_WRITE_EXPRESSION =
    "hasAnyAuthority('ROLE_ADMIN','users:write','SCOPE_users:write')";

  private UserAuthorities() {}

  public static boolean hasUsersRead(Authentication authentication) {
    return hasAnyAuthority(authentication, USERS_READ_AUTHORITIES);
  }

  public static boolean hasUsersWrite(Authentication authentication) {
    return hasAnyAuthority(authentication, USERS_WRITE_AUTHORITIES);
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
