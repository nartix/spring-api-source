package com.ferozfaiz.uniapi.iam.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminAuthoritiesTest {

  @Test
  void loginEligibilityIncludesAdminModeratorAndAdminAccess() {
    assertThat(AdminAuthorities.ADMIN_LOGIN_AUTHORITIES).containsExactlyInAnyOrder(
      "ROLE_ADMIN",
      "ROLE_MODERATOR",
      "admin:access"
    );
  }

  @Test
  void detectsAdminAndModeratorAuthorities() {
    assertThat(AdminAuthorities.hasAdminLoginAuthority(authentication("ROLE_ADMIN"))).isTrue();
    assertThat(AdminAuthorities.hasAdminLoginAuthority(authentication("ROLE_MODERATOR"))).isTrue();
    assertThat(AdminAuthorities.hasAdminLoginAuthority(authentication("admin:access"))).isTrue();
    assertThat(AdminAuthorities.hasAdminLoginAuthority(authentication("ROLE_USER"))).isFalse();
  }

  @Test
  void userManagementAuthorityDoesNotGrantModeratorByDefault() {
    assertThat(AdminAuthorities.hasAdminUsersRead(authentication("ROLE_MODERATOR"))).isFalse();
    assertThat(AdminAuthorities.hasAdminUsersStatus(authentication("ROLE_MODERATOR"))).isFalse();
    assertThat(AdminAuthorities.hasAdminUsersWrite(authentication("ROLE_MODERATOR"))).isFalse();
    assertThat(AdminAuthorities.hasAdminUsersRead(authentication("admin:users:read"))).isTrue();
    assertThat(AdminAuthorities.hasAdminUsersStatus(authentication("admin:users:status"))).isTrue();
    assertThat(AdminAuthorities.hasAdminUsersWrite(authentication("admin:users:write"))).isTrue();
  }

  private static TestingAuthenticationToken authentication(String authority) {
    return new TestingAuthenticationToken(
      "principal",
      null,
      List.of(new SimpleGrantedAuthority(authority))
    );
  }
}
