package com.ferozfaiz.uniapi.iam.application.policy;

import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessPolicy {

  public boolean canAccessAdminArea(Authentication authentication) {
    return AdminAuthorities.hasAdminLoginAuthority(authentication);
  }

  public boolean canReadUsers(Authentication authentication) {
    return AdminAuthorities.hasAdminUsersRead(authentication);
  }

  public boolean canUpdateUserStatus(Authentication authentication) {
    return AdminAuthorities.hasAdminUsersStatus(authentication);
  }

  public boolean canWriteUsers(Authentication authentication) {
    return AdminAuthorities.hasAdminUsersWrite(authentication);
  }

  public boolean canModerateProducts(Authentication authentication) {
    return hasAuthority(authentication, AdminAuthorities.ADMIN_PRODUCTS_MODERATE);
  }

  private static boolean hasAuthority(Authentication authentication, String authority) {
    return (
      authentication != null &&
      authentication.isAuthenticated() &&
      authentication
        .getAuthorities()
        .stream()
        .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority))
    );
  }
}
