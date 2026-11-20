package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionCoexistenceIntegrationTest {

  private final SessionCookieProperties properties = new SessionCookieProperties();
  private final ScopedCookieHttpSessionIdResolver resolver = new ScopedCookieHttpSessionIdResolver(
    properties
  );

  @Test
  void sameBrowserRegularAndAdminRequestsResolveOnlyTheirOwnCookie() {
    MockHttpServletRequest regular = request("/api/v1/auth/me");
    MockHttpServletRequest admin = request("/api/v1/admin/auth/me");

    assertThat(resolver.resolveSessionIds(regular)).containsExactly("user-session");
    assertThat(resolver.resolveSessionIds(admin)).containsExactly("admin-session");
  }

  @Test
  void adminPathSelectionRequiresExactAdminPathBoundary() {
    assertThat(resolver.resolveScope(request("/api/v1/admin"))).isEqualTo(SessionCookieScope.ADMIN);
    assertThat(resolver.resolveScope(request("/api/v1/admin/users"))).isEqualTo(
      SessionCookieScope.ADMIN
    );
    assertThat(resolver.resolveScope(request("/api/v1/administer"))).isEqualTo(
      SessionCookieScope.USER
    );
  }

  private MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    request.setCookies(
      new Cookie(properties.userCookieName(), "user-session"),
      new Cookie(properties.adminCookieName(), "admin-session")
    );
    return request;
  }
}
