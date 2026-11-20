package com.ferozfaiz.uniapi.platform.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AdminAuthenticationProviderTest {

  @Test
  void allowsAdminAuthority() {
    var provider = provider(Set.of("ADMIN"), Set.of());

    var authentication = provider.authenticate(login());

    assertThat(authentication.isAuthenticated()).isTrue();
  }

  @Test
  void allowsModeratorAuthority() {
    var provider = provider(Set.of("MODERATOR"), Set.of());

    var authentication = provider.authenticate(login());

    assertThat(authentication.isAuthenticated()).isTrue();
  }

  @Test
  void rejectsRegularUserWithGenericFailure() {
    var provider = provider(Set.of("USER"), Set.of("user:profile:read"));

    assertThatThrownBy(() -> provider.authenticate(login()))
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid username or password");
  }

  @Test
  void allowsExplicitAdminAccessPermission() {
    var provider = provider(Set.of("USER"), Set.of("admin:access"));

    var authentication = provider.authenticate(login());

    assertThat(authentication.isAuthenticated()).isTrue();
  }

  private static AdminAuthenticationProvider provider(Set<String> roles, Set<String> permissions) {
    var delegate = new IamAuthenticationProvider(
      (username, password) -> new AuthenticatedIdentity(1L, "feroz", true, true, true, true),
      userId -> new AuthenticatedAuthorities(roles, permissions)
    );
    return new AdminAuthenticationProvider(delegate);
  }

  private static UsernamePasswordAuthenticationToken login() {
    return UsernamePasswordAuthenticationToken.unauthenticated("feroz", "secret");
  }
}
