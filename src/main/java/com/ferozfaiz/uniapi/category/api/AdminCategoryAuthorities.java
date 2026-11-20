package com.ferozfaiz.uniapi.category.api;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.Set;
import org.springframework.security.core.Authentication;

public final class AdminCategoryAuthorities {

  private AdminCategoryAuthorities() {}

  public static String readPermission(String systemKey) {
    return "admin:" + normalize(systemKey) + "-categories:read";
  }

  public static String writePermission(String systemKey) {
    return "admin:" + normalize(systemKey) + "-categories:write";
  }

  public static boolean hasSystemRead(Authentication authentication, String systemKey) {
    return hasAuthority(authentication, readPermission(systemKey));
  }

  public static boolean hasSystemWrite(Authentication authentication, String systemKey) {
    return hasAuthority(authentication, writePermission(systemKey));
  }

  public static String standardPermission(String systemKey, Operation operation) {
    if (operation == null) {
      throw new IllegalArgumentException("operation must not be null");
    }
    return switch (operation) {
      case CAPABILITIES, LIST, TREE, PARENT_OPTIONS, GET, BREADCRUMB -> readPermission(systemKey);
      case CREATE, REPLACE, PATCH, DELETE, MOVE, REORDER, ACTIVATE, DEACTIVATE -> writePermission(
        systemKey
      );
    };
  }

  public static boolean hasPermission(Authentication authentication, String permission) {
    return permission != null && hasAuthority(authentication, permission);
  }

  private static boolean hasAuthority(Authentication authentication, String authority) {
    return (
      authentication != null &&
      authentication.isAuthenticated() &&
      authentication
        .getAuthorities()
        .stream()
        .map(grantedAuthority -> grantedAuthority.getAuthority())
        .anyMatch(Set.of(authority)::contains)
    );
  }

  private static String normalize(String systemKey) {
    if (systemKey == null || systemKey.isBlank()) {
      throw new IllegalArgumentException("systemKey must not be blank");
    }
    return systemKey.trim();
  }
}
