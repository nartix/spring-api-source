package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminSessionSecurityTest {

  private static final Path CONFIGURATION = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/platform/security/session/AdminSessionSecurityConfiguration.java"
  );

  @Test
  void adminCsrfRepositoryUsesAdminCookieAndHeader() {
    SessionCookieProperties properties = new SessionCookieProperties();
    var repository = ScopedCookieCsrfTokenRepository.create(properties, SessionCookieScope.ADMIN);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/csrf");
    MockHttpServletResponse response = new MockHttpServletResponse();

    var token = repository.generateToken(request);
    repository.saveToken(token, request, response);

    assertThat(token.getHeaderName()).isEqualTo(properties.adminCsrfHeaderName());
    assertThat(response.getCookie(properties.adminCsrfCookieName())).isNotNull();
    assertThat(response.getCookie(properties.userCsrfCookieName())).isNull();
  }

  @Test
  void adminSecurityChainUsesJsonLoginLogoutAndSameScopeCookieExpiry() throws IOException {
    String source = Files.readString(CONFIGURATION);

    assertThat(source)
      .contains(".securityMatcher(basePath + \"/admin/**\")")
      .contains(".loginProcessingUrl(basePath + \"/admin/auth/login\")")
      .contains(".logoutUrl(basePath + \"/admin/auth/logout\")")
      .contains(".deleteCookies(properties.adminCookieName(), properties.adminCsrfCookieName())")
      .contains("adminLoginSuccessHandler()")
      .contains("adminLoginFailureHandler()")
      .contains("adminLogoutSuccessHandler()");
  }
}
