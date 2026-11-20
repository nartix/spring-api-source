package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WrongScopeSessionContractTest {

  private final SessionCookieProperties properties = new SessionCookieProperties();
  private final ScopedCookieHttpSessionIdResolver resolver = new ScopedCookieHttpSessionIdResolver(
    properties
  );

  @Test
  void regularEndpointIgnoresAdminCookie() {
    MockHttpServletRequest request = request("/api/v1/auth/me");
    request.setCookies(new Cookie(properties.adminCookieName(), "admin-session"));

    assertThat(resolver.resolveSessionIds(request)).isEmpty();
  }

  @Test
  void adminEndpointIgnoresRegularCookie() {
    MockHttpServletRequest request = request("/api/v1/admin/auth/me");
    request.setCookies(new Cookie(properties.userCookieName(), "user-session"));

    assertThat(resolver.resolveSessionIds(request)).isEmpty();
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    return request;
  }
}
