package com.ferozfaiz.uniapi.iam.application.policy;

import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import com.ferozfaiz.uniapi.iam.api.UserAuthorities;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("userReadAuthorizationPolicy")
public class UserReadAuthorizationPolicy {

  public boolean canRead(Authentication authentication) {
    return (
      UserAuthorities.hasUsersRead(authentication) ||
      AdminAuthorities.hasAdminUsersRead(authentication)
    );
  }

  public boolean canRead(Authentication authentication, Long userId) {
    return canRead(authentication);
  }
}
