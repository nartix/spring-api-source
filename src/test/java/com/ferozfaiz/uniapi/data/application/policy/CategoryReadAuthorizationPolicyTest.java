package com.ferozfaiz.uniapi.data.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.data.api.CategoryAuthorities;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CategoryReadAuthorizationPolicyTest {

  private final CategoryReadAuthorizationPolicy policy = new CategoryReadAuthorizationPolicy();

  @Test
  void rejectsMissingReadAuthorityAndAdminBypass() {
    assertThat(policy.canRead(null)).isFalse();
    assertThat(policy.canRead(authentication())).isFalse();
    assertThat(policy.canRead(authentication(CategoryAuthorities.CATEGORIES_WRITE), 42L)).isFalse();
    assertThat(policy.canRead(authentication("ROLE_ADMIN"), 42L)).isFalse();
  }

  @Test
  void allowsRawAndScopeReadAuthoritiesWithoutRecordRestriction() {
    assertThat(policy.canRead(authentication(CategoryAuthorities.CATEGORIES_READ))).isTrue();
    assertThat(
      policy.canRead(authentication(CategoryAuthorities.SCOPE_CATEGORIES_READ), 42L)
    ).isTrue();
  }

  private static Authentication authentication(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
      "feroz",
      "n/a",
      Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
    );
  }
}
