package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RegularUserSessionSecurityTest {

  private static final Path CONFIGURATION = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/platform/security/session/RegularSessionSecurityConfiguration.java"
  );

  @Test
  void regularCsrfRepositoryUsesUserCookieAndHeader() {
    SessionCookieProperties properties = new SessionCookieProperties();
    var repository = ScopedCookieCsrfTokenRepository.create(properties, SessionCookieScope.USER);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
    MockHttpServletResponse response = new MockHttpServletResponse();

    var token = repository.generateToken(request);
    repository.saveToken(token, request, response);

    assertThat(token.getHeaderName()).isEqualTo(properties.userCsrfHeaderName());
    assertThat(response.getCookie(properties.userCsrfCookieName())).isNotNull();
    assertThat(response.getCookie(properties.adminCsrfCookieName())).isNull();
  }

  @Test
  void regularSecurityChainUsesJsonLoginLogoutAndSameScopeCookieExpiry() throws IOException {
    String source = Files.readString(CONFIGURATION);

    assertThat(source)
      .contains(".securityMatcher(basePath + \"/auth/**\")")
      .contains(".loginProcessingUrl(basePath + \"/auth/login\")")
      .contains(".logoutUrl(basePath + \"/auth/logout\")")
      .contains(".deleteCookies(properties.userCookieName(), properties.userCsrfCookieName())")
      .contains("regularLoginSuccessHandler()")
      .contains("regularLoginFailureHandler()")
      .contains("regularLogoutSuccessHandler()");
  }
}
