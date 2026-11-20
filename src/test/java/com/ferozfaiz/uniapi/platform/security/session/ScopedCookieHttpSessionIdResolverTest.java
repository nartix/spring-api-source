package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ScopedCookieHttpSessionIdResolverTest {

  private final SessionCookieProperties properties = new SessionCookieProperties();
  private final ScopedCookieHttpSessionIdResolver resolver = new ScopedCookieHttpSessionIdResolver(
    properties
  );

  @Test
  void adminPathReadsOnlyAdminCookie() {
    MockHttpServletRequest request = request("/api/v1/admin/users");
    request.setCookies(
      new Cookie(properties.userCookieName(), "user-session"),
      new Cookie(properties.adminCookieName(), "admin-session")
    );

    assertThat(resolver.resolveScope(request)).isEqualTo(SessionCookieScope.ADMIN);
    assertThat(resolver.resolveSessionIds(request)).containsExactly("admin-session");
  }

  @Test
  void regularPathReadsOnlyUserCookie() {
    MockHttpServletRequest request = request("/api/v1/auth/me");
    request.setCookies(
      new Cookie(properties.userCookieName(), "user-session"),
      new Cookie(properties.adminCookieName(), "admin-session")
    );

    assertThat(resolver.resolveScope(request)).isEqualTo(SessionCookieScope.USER);
    assertThat(resolver.resolveSessionIds(request)).containsExactly("user-session");
  }

  @Test
  void writesOnlyCurrentScopeCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    resolver.setSessionId(request("/api/v1/admin/auth/login"), response, "admin-session");

    assertThat(response.getCookie(properties.adminCookieName())).isNotNull();
    assertThat(response.getCookie(properties.userCookieName())).isNull();
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    return request;
  }
}
