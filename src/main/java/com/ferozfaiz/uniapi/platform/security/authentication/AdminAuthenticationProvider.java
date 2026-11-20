package com.ferozfaiz.uniapi.platform.security.authentication;

import com.ferozfaiz.uniapi.iam.api.AdminAuthorities;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {

  private static final String GENERIC_ADMIN_LOGIN_FAILURE = "Invalid username or password";

  private final IamAuthenticationProvider delegate;

  public AdminAuthenticationProvider(IamAuthenticationProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    Authentication authenticated;
    try {
      authenticated = delegate.authenticate(authentication);
    } catch (AuthenticationException exception) {
      throw genericFailure(exception);
    }

    if (authenticated == null || !AdminAuthorities.hasAdminLoginAuthority(authenticated)) {
      throw genericFailure(null);
    }

    return authenticated;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return delegate.supports(authentication);
  }

  private static BadCredentialsException genericFailure(AuthenticationException cause) {
    return cause == null
      ? new BadCredentialsException(GENERIC_ADMIN_LOGIN_FAILURE)
      : new BadCredentialsException(GENERIC_ADMIN_LOGIN_FAILURE, cause);
  }
}
