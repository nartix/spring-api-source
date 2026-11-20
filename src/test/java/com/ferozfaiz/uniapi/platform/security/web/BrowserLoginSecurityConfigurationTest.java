package com.ferozfaiz.uniapi.platform.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BrowserLoginSecurityConfigurationTest {

  private static final Path CONFIGURATION = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/platform/security/web/BrowserLoginSecurityConfiguration.java"
  );

  @Test
  void swaggerAndOpenApiDocsDoNotUseBrowserLoginFallback() throws IOException {
    String source = Files.readString(CONFIGURATION);

    assertThat(source)
      .contains("\"/swagger-ui.html\", \"/swagger-ui/**\", \"/v3/api-docs\", \"/v3/api-docs/**\"")
      .contains(".permitAll()")
      .contains(".formLogin(Customizer.withDefaults())");
  }
}
