package com.ferozfaiz.uniapi.platform.security.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegularApiSessionCsrfCompatibilityTest {

  private static final Path CONFIGURATION = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/platform/security/resourceserver/ResourceServerSecurityConfiguration.java"
  );

  @Test
  void bearerRequestsKeepStatelessCsrfBehaviorWhileSessionRequestsUseRegularCsrf()
    throws IOException {
    String source = Files.readString(CONFIGURATION);

    assertThat(source)
      .contains(
        "ScopedCookieCsrfTokenRepository.create(sessionCookieProperties, SessionCookieScope.USER)"
      )
      .contains("!hasCookie(request, sessionCookieProperties.userCookieName())")
      .contains("hasBearerAuthorization");
  }
}
