package com.ferozfaiz.uniapi.iam.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminAccessPolicyTest {

  private final AdminAccessPolicy policy = new AdminAccessPolicy();

  @Test
  void adminAndModeratorCanEnterAdminArea() {
    assertThat(policy.canAccessAdminArea(authentication("ROLE_ADMIN"))).isTrue();
    assertThat(policy.canAccessAdminArea(authentication("ROLE_MODERATOR"))).isTrue();
    assertThat(policy.canAccessAdminArea(authentication("ROLE_USER"))).isFalse();
  }

  @Test
  void moderatorUserManagementIsOptInByPermission() {
    assertThat(policy.canReadUsers(authentication("ROLE_MODERATOR"))).isFalse();
    assertThat(policy.canUpdateUserStatus(authentication("ROLE_MODERATOR"))).isFalse();
    assertThat(policy.canWriteUsers(authentication("ROLE_MODERATOR"))).isFalse();
    assertThat(policy.canReadUsers(authentication("admin:users:read"))).isTrue();
    assertThat(policy.canUpdateUserStatus(authentication("admin:users:status"))).isTrue();
    assertThat(policy.canWriteUsers(authentication("admin:users:write"))).isTrue();
  }

  @Test
  void moderationPermissionAllowsProductModeration() {
    assertThat(policy.canModerateProducts(authentication("admin:products:moderate"))).isTrue();
    assertThat(policy.canModerateProducts(authentication("ROLE_MODERATOR"))).isFalse();
  }

  private static TestingAuthenticationToken authentication(String authority) {
    return new TestingAuthenticationToken(
      "principal",
      null,
      List.of(new SimpleGrantedAuthority(authority))
    );
  }
}
