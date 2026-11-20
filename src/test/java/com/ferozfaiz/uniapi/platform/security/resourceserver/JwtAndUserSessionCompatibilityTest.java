package com.ferozfaiz.uniapi.platform.security.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JwtAndUserSessionCompatibilityTest {

  private static final Path CONFIGURATION = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/platform/security/resourceserver/ResourceServerSecurityConfiguration.java"
  );

  @Test
  void resourceServerChainSupportsJwtAndRegularUserSessionCompatibility() throws IOException {
    String source = Files.readString(CONFIGURATION);

    assertThat(source)
      .contains("SessionCreationPolicy.IF_REQUIRED")
      .contains("SessionCookieScope.USER")
      .contains("JwtSessionCredentialConflictFilter")
      .contains("BearerTokenAuthenticationFilter.class");
  }
}
