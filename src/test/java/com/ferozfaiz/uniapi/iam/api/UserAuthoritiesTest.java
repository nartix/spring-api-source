package com.ferozfaiz.uniapi.iam.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserAuthoritiesTest {

  @Test
  void userReadContractAcceptsRawScopeAndIamAdminAuthorities() {
    assertThat(UserAuthorities.USERS_READ_AUTHORITIES).containsExactlyInAnyOrder(
      UserAuthorities.ROLE_ADMIN,
      UserAuthorities.USERS_READ,
      UserAuthorities.SCOPE_USERS_READ
    );

    assertThat(UserAuthorities.hasUsersRead(authentication(UserAuthorities.USERS_READ))).isTrue();
    assertThat(
      UserAuthorities.hasUsersRead(authentication(UserAuthorities.SCOPE_USERS_READ))
    ).isTrue();
    assertThat(UserAuthorities.hasUsersRead(authentication(UserAuthorities.ROLE_ADMIN))).isTrue();
    assertThat(UserAuthorities.hasUsersRead(authentication(UserAuthorities.USERS_WRITE))).isFalse();
  }

  @Test
  void userWriteContractAcceptsRawScopeAndIamAdminAuthorities() {
    assertThat(UserAuthorities.USERS_WRITE_AUTHORITIES).containsExactlyInAnyOrder(
      UserAuthorities.ROLE_ADMIN,
      UserAuthorities.USERS_WRITE,
      UserAuthorities.SCOPE_USERS_WRITE
    );

    assertThat(UserAuthorities.hasUsersWrite(authentication(UserAuthorities.USERS_WRITE))).isTrue();
    assertThat(
      UserAuthorities.hasUsersWrite(authentication(UserAuthorities.SCOPE_USERS_WRITE))
    ).isTrue();
    assertThat(UserAuthorities.hasUsersWrite(authentication(UserAuthorities.ROLE_ADMIN))).isTrue();
    assertThat(UserAuthorities.hasUsersWrite(authentication(UserAuthorities.USERS_READ))).isFalse();
  }

  @Test
  void missingAuthenticationOrAuthoritiesAreDenied() {
    assertThat(UserAuthorities.hasUsersRead(null)).isFalse();
    assertThat(UserAuthorities.hasUsersWrite(authentication())).isFalse();
  }

  private static Authentication authentication(String... authorities) {
    return UsernamePasswordAuthenticationToken.authenticated(
      "feroz",
      "n/a",
      Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList()
    );
  }
}
