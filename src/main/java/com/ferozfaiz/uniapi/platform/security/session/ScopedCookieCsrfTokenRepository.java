package com.ferozfaiz.uniapi.platform.security.session;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

public final class ScopedCookieCsrfTokenRepository {

  private static final String CSRF_PARAMETER_NAME = "_csrf";

  private ScopedCookieCsrfTokenRepository() {}

  public static CookieCsrfTokenRepository create(
    SessionCookieProperties properties,
    SessionCookieScope scope
  ) {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieName(properties.csrfCookieName(scope));
    repository.setHeaderName(properties.csrfHeaderName(scope));
    repository.setParameterName(CSRF_PARAMETER_NAME);
    repository.setCookiePath(properties.sessionCookiePath(scope));
    repository.setCookieCustomizer(cookie ->
      cookie
        .sameSite(properties.sameSite())
        .secure(properties.secure())
        .httpOnly(false)
        .path(properties.sessionCookiePath(scope))
    );
    return repository;
  }
}
