package com.ferozfaiz.uniapi.platform.security.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResourceServerSecurityConfigurationTest {

  private static final Path CONFIGURATION = Path.of(
    "src/main/java/com/ferozfaiz/uniapi/platform/security/resourceserver/ResourceServerSecurityConfiguration.java"
  );

  @Test
  void apiSecurityChainKeepsOnlyCoarsePerimeterResponsibilities() throws IOException {
    String source = source();

    assertThat(source)
      .contains(".securityMatcher(basePath + \"/**\")")
      .contains(
        "ScopedCookieCsrfTokenRepository.create(sessionCookieProperties, SessionCookieScope.USER)"
      )
      .contains("SessionCreationPolicy.IF_REQUIRED")
      .contains(".anyRequest()")
      .contains(".authenticated()")
      .contains(".oauth2ResourceServer(")
      .contains("jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)")
      .contains("JwtSessionCredentialConflictFilter");

    assertThat(source)
      .doesNotContain("basePath + \"/products")
      .doesNotContain("basePath + \"/storage-products")
      .doesNotContain("basePath + \"/cpus")
      .doesNotContain("basePath + \"/graphics-cards")
      .doesNotContain("basePath + \"/categories/*/products")
      .doesNotContain("basePath + \"/catalogue/")
      .doesNotContain("SCOPE_catalogue:read")
      .doesNotContain("SCOPE_catalogue:write")
      .doesNotContain("CATALOGUE_READ")
      .doesNotContain("CATALOGUE_WRITE")
      .doesNotContain("SERVICE_CATALOGUE");
  }

  @Test
  void jsonSecurityErrorsKeepStandardApiResponseContract() throws IOException {
    assertThat(source())
      .contains("authenticationEntryPoint")
      .contains("accessDeniedHandler")
      .contains("HttpStatus.UNAUTHORIZED")
      .contains("\"UNAUTHENTICATED\"")
      .contains("\"Authentication is required\"")
      .contains("HttpStatus.FORBIDDEN")
      .contains("\"FORBIDDEN\"")
      .contains("\"Access is denied\"")
      .contains("ApiErrorResponse.of(code, message, request.getRequestURI())");
  }

  private static String source() throws IOException {
    return Files.readString(CONFIGURATION);
  }
}
