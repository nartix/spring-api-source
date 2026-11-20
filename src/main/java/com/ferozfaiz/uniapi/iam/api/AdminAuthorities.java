package com.ferozfaiz.uniapi.iam.api;

import java.util.Set;
import org.springframework.security.core.Authentication;

public final class AdminAuthorities {

  public static final String ROLE_ADMIN = "ROLE_ADMIN";
  public static final String ROLE_MODERATOR = "ROLE_MODERATOR";
  public static final String ADMIN_ACCESS = "admin:access";
  public static final String ADMIN_USERS_READ = "admin:users:read";
  public static final String ADMIN_USERS_STATUS = "admin:users:status";
  public static final String ADMIN_USERS_WRITE = "admin:users:write";
  public static final String ADMIN_PRODUCTS_READ = "admin:products:read";
  public static final String ADMIN_PRODUCTS_WRITE = "admin:products:write";
  public static final String ADMIN_PRODUCTS_MODERATE = "admin:products:moderate";
  public static final String ADMIN_SESSIONS_READ = "admin:sessions:read";
  public static final String ADMIN_SESSIONS_REVOKE = "admin:sessions:revoke";

  public static final Set<String> ADMIN_LOGIN_AUTHORITIES = Set.of(
    ROLE_ADMIN,
    ROLE_MODERATOR,
    ADMIN_ACCESS
  );
  public static final Set<String> ADMIN_USERS_READ_AUTHORITIES = Set.of(
    ROLE_ADMIN,
    ADMIN_USERS_READ
  );
  public static final Set<String> ADMIN_USERS_STATUS_AUTHORITIES = Set.of(
    ROLE_ADMIN,
    ADMIN_USERS_STATUS
  );
  public static final Set<String> ADMIN_USERS_WRITE_AUTHORITIES = Set.of(
    ROLE_ADMIN,
    ADMIN_USERS_WRITE
  );
  public static final String ADMIN_ACCESS_EXPRESSION =
    "hasAnyAuthority('ROLE_ADMIN','ROLE_MODERATOR','admin:access')";
  public static final String ADMIN_USERS_READ_EXPRESSION =
    "hasAnyAuthority('ROLE_ADMIN','admin:users:read')";
  public static final String ADMIN_USERS_STATUS_EXPRESSION =
    "hasAnyAuthority('ROLE_ADMIN','admin:users:status')";
  public static final String ADMIN_USERS_WRITE_EXPRESSION =
    "hasAnyAuthority('ROLE_ADMIN','admin:users:write')";

  private AdminAuthorities() {}

  public static boolean hasAdminLoginAuthority(Authentication authentication) {
    return hasAnyAuthority(authentication, ADMIN_LOGIN_AUTHORITIES);
  }

  public static boolean hasAdminUsersRead(Authentication authentication) {
    return hasAnyAuthority(authentication, ADMIN_USERS_READ_AUTHORITIES);
  }

  public static boolean hasAdminUsersStatus(Authentication authentication) {
    return hasAnyAuthority(authentication, ADMIN_USERS_STATUS_AUTHORITIES);
  }

  public static boolean hasAdminUsersWrite(Authentication authentication) {
    return hasAnyAuthority(authentication, ADMIN_USERS_WRITE_AUTHORITIES);
  }

  public static boolean isModerator(Authentication authentication) {
    return hasAnyAuthority(authentication, Set.of(ROLE_MODERATOR));
  }

  public static boolean isAdmin(Authentication authentication) {
    return hasAnyAuthority(authentication, Set.of(ROLE_ADMIN, ADMIN_ACCESS));
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
