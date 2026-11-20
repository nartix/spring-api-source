package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionCsrfContractTest {

  @Test
  void regularCsrfContractUsesRegularCookieAndHeader() {
    assertCsrfContract(SessionCookieScope.USER);
  }

  @Test
  void adminCsrfContractUsesAdminCookieAndHeader() {
    assertCsrfContract(SessionCookieScope.ADMIN);
  }

  private static void assertCsrfContract(SessionCookieScope scope) {
    SessionCookieProperties properties = new SessionCookieProperties();
    var repository = ScopedCookieCsrfTokenRepository.create(properties, scope);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
    MockHttpServletResponse response = new MockHttpServletResponse();

    var token = repository.generateToken(request);
    repository.saveToken(token, request, response);

    assertThat(token.getHeaderName()).isEqualTo(properties.csrfHeaderName(scope));
    assertThat(token.getParameterName()).isEqualTo("_csrf");
    assertThat(response.getCookie(properties.csrfCookieName(scope))).isNotNull();
    assertThat(response.getCookie(properties.csrfCookieName(scope)).isHttpOnly()).isFalse();
    assertThat(response.getCookie(properties.csrfCookieName(scope)).getSecure()).isTrue();
  }
}
