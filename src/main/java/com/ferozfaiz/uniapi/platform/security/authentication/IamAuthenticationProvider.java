package com.ferozfaiz.uniapi.platform.security.authentication;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedIdentity;
import com.ferozfaiz.uniapi.iam.api.auth.AuthenticationFailedException;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthenticator;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class IamAuthenticationProvider implements AuthenticationProvider {

  private final IdentityAuthenticator authenticator;
  private final IdentityAuthorityLookup authorityLookup;

  public IamAuthenticationProvider(
    IdentityAuthenticator authenticator,
    IdentityAuthorityLookup authorityLookup
  ) {
    this.authenticator = authenticator;
    this.authorityLookup = authorityLookup;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
      return null;
    }

    String usernameOrEmail = authentication.getName();
    String rawPassword =
      authentication.getCredentials() == null ? null : authentication.getCredentials().toString();

    AuthenticatedIdentity identity;
    try {
      identity = authenticator.authenticate(usernameOrEmail, rawPassword);
    } catch (AuthenticationFailedException exception) {
      throw toSpringAuthenticationException(exception);
    }

    AuthenticatedAuthorities authenticatedAuthorities = authorityLookup.findAuthorities(
      identity.userId()
    );
    Collection<GrantedAuthority> authorities = toGrantedAuthorities(authenticatedAuthorities);

    return UsernamePasswordAuthenticationToken.authenticated(
      new AuthenticatedPrincipal(
        identity.userId(),
        identity.username(),
        authenticatedAuthorities.roles(),
        authenticatedAuthorities.permissions()
      ),
      null,
      authorities
    );
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  private AuthenticationException toSpringAuthenticationException(
    AuthenticationFailedException exception
  ) {
    return switch (exception.reason()) {
      case DISABLED -> new DisabledException(exception.getMessage(), exception);
      case EXPIRED -> new AccountExpiredException(exception.getMessage(), exception);
      case LOCKED -> new LockedException(exception.getMessage(), exception);
      case CREDENTIALS_EXPIRED -> new CredentialsExpiredException(
        exception.getMessage(),
        exception
      );
      case INVALID_CREDENTIALS -> new BadCredentialsException(exception.getMessage(), exception);
    };
  }

  private static Collection<GrantedAuthority> toGrantedAuthorities(
    AuthenticatedAuthorities authenticatedAuthorities
  ) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authenticatedAuthorities
      .roles()
      .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
    authenticatedAuthorities
      .permissions()
      .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
    return List.copyOf(authorities);
  }
}
