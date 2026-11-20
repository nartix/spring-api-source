package com.ferozfaiz.uniapi.iam.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.iam.api.UserAuthorities;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserReadAuthorizationPolicyTest {

  private final UserReadAuthorizationPolicy policy = new UserReadAuthorizationPolicy();

  @Test
  void rejectsMissingReadAuthority() {
    assertThat(policy.canRead(null)).isFalse();
    assertThat(policy.canRead(authentication())).isFalse();
    assertThat(policy.canRead(authentication(UserAuthorities.USERS_WRITE), 42L)).isFalse();
  }

  @Test
  void allowsRawScopeAndIamAdminReadAuthorities() {
    assertThat(policy.canRead(authentication(UserAuthorities.USERS_READ))).isTrue();
    assertThat(policy.canRead(authentication(UserAuthorities.SCOPE_USERS_READ), 42L)).isTrue();
    assertThat(policy.canRead(authentication(UserAuthorities.ROLE_ADMIN), 42L)).isTrue();
    assertThat(policy.canRead(authentication("admin:users:read"), 42L)).isTrue();
  }

  private static Authentication authentication(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
      "feroz",
      "n/a",
      Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
    );
  }
}
