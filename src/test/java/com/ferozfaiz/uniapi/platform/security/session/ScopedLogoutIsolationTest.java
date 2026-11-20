package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ScopedLogoutIsolationTest {

  private final SessionCookieProperties properties = properties();
  private final SessionJsonAuthenticationHandlers handlers = handlers();

  @Test
  void regularLogoutExpiresOnlyRegularCsrfCookie() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .regularLogoutSuccessHandler()
      .onLogoutSuccess(request("/api/v1/auth/logout"), response, userAuthentication());

    assertThat(response.getCookie(properties.userCsrfCookieName()).getMaxAge()).isZero();
    assertThat(response.getCookie(properties.adminCsrfCookieName())).isNull();
  }

  @Test
  void adminLogoutExpiresOnlyAdminCsrfCookie() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handlers
      .adminLogoutSuccessHandler()
      .onLogoutSuccess(request("/api/v1/admin/auth/logout"), response, adminAuthentication());

    assertThat(response.getCookie(properties.adminCsrfCookieName()).getMaxAge()).isZero();
    assertThat(response.getCookie(properties.userCsrfCookieName())).isNull();
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRequestURI(path);
    request.setRequestedSessionId("raw-session-id");
    return request;
  }

  private static TestingAuthenticationToken userAuthentication() {
    return new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, "feroz", Set.of("USER"), Set.of()),
      null
    );
  }

  private static TestingAuthenticationToken adminAuthentication() {
    return new TestingAuthenticationToken(
      new AuthenticatedPrincipal(2L, "admin", Set.of("ADMIN"), Set.of()),
      null,
      java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    );
  }

  private SessionJsonAuthenticationHandlers handlers() {
    MessageResolver messages = (code, args) -> code;
    return new SessionJsonAuthenticationHandlers(
      new ObjectMapper().findAndRegisterModules(),
      messages,
      new ArrayList<>()::add,
      new SessionIdHashingService(properties),
      properties,
      userId -> {}
    );
  }

  private static SessionCookieProperties properties() {
    SessionCookieProperties properties = new SessionCookieProperties();
    properties.setAuditHmacSecret("local-test-session-audit-hmac-secret");
    return properties;
  }
}
