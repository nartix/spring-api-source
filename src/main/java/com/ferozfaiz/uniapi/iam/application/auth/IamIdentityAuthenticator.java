package com.ferozfaiz.uniapi.iam.application.auth;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticationFailedException;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthenticator;
import com.ferozfaiz.uniapi.iam.application.port.PasswordHasher;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRepository;
import com.ferozfaiz.uniapi.shared.text.Text;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class IamIdentityAuthenticator implements IdentityAuthenticator {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;

  IamIdentityAuthenticator(UserRepository users, PasswordHasher passwordHasher) {
    this.users = users;
    this.passwordHasher = passwordHasher;
  }

  @Override
  public AuthenticatedIdentity authenticate(String usernameOrEmail, String rawPassword) {
    String login = requireText(usernameOrEmail);
    String password = requireText(rawPassword);
    User user = users
      .findByUsername(login)
      .or(() -> users.findByEmail(login.toLowerCase(Locale.ROOT)))
      .orElseThrow(AuthenticationFailedException::invalidCredentials);

    if (!passwordHasher.matches(password, user.passwordHash())) {
      throw AuthenticationFailedException.invalidCredentials();
    }
    if (!user.active()) {
      throw AuthenticationFailedException.disabled();
    }
    if (!user.accountNonExpired()) {
      throw AuthenticationFailedException.expired();
    }
    if (!user.accountNonLocked()) {
      throw AuthenticationFailedException.locked();
    }
    if (!user.credentialsNonExpired()) {
      throw AuthenticationFailedException.credentialsExpired();
    }

    return new AuthenticatedIdentity(
      user.id(),
      user.username(),
      user.active(),
      user.accountNonExpired(),
      user.accountNonLocked(),
      user.credentialsNonExpired()
    );
  }

  private static String requireText(String value) {
    String trimmed = Text.trimToNull(value);
    if (trimmed == null) {
      throw AuthenticationFailedException.invalidCredentials();
    }
    return trimmed;
  }
}
