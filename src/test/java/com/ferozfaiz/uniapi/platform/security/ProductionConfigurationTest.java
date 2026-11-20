package com.ferozfaiz.uniapi.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionConfigurationTest {

  private static final String[] VALID_SETTINGS = {
    "spring.datasource.url=jdbc:postgresql://database:5432/portfolio",
    "spring.datasource.username=fixture",
    "spring.datasource.password=private-password-sentinel",
    "app.security.session.secure=true",
    "app.security.session.audit-hmac-secret=fixture-session-secret-long-enough-for-test",
    "app.security.oauth2.issuer=https://admin.example.test",
    "app.security.oauth2.signing.allow-generated-key=false",
    "app.security.oauth2.signing.key-id=portfolio-fixture",
    "app.security.oauth2.signing.public-key-pem-base64=public-key-fixture",
    "app.security.oauth2.signing.private-key-pem-base64=private-key-sentinel",
    "app.security.oauth2.clients.nextjs.client-secret=fixture-nextjs-secret-long-enough-for-test",
    "app.security.oauth2.clients.nextjs.redirect-uris[0]=https://admin.example.test/api/auth/callback/uniapi",
    "app.security.oauth2.clients.nextjs.post-logout-redirect-uris[0]=https://admin.example.test",
    "app.security.oauth2.clients.nextjs-server.client-secret=fixture-server-secret-long-enough-for-test",
  };

  private ApplicationContextRunner production() {
    return new ApplicationContextRunner()
      .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
      .withUserConfiguration(ProductionConfiguration.class);
  }

  @Test
  void acceptsCompleteProductionSettingsWithoutConnectingToServices() {
    production()
      .withPropertyValues(VALID_SETTINGS)
      .run(context -> assertThat(context).hasNotFailed());
  }

  @ParameterizedTest
  @ValueSource(
    strings = {
      "spring.datasource.url",
      "spring.datasource.username",
      "spring.datasource.password",
      "app.security.session.audit-hmac-secret",
      "app.security.oauth2.issuer",
      "app.security.oauth2.signing.key-id",
      "app.security.oauth2.signing.private-key-pem-base64",
      "app.security.oauth2.signing.public-key-pem-base64",
      "app.security.oauth2.clients.nextjs.client-secret",
      "app.security.oauth2.clients.nextjs.redirect-uris[0]",
      "app.security.oauth2.clients.nextjs.post-logout-redirect-uris[0]",
      "app.security.oauth2.clients.nextjs-server.client-secret",
    }
  )
  void missingSettingsStopStartupWithoutDisclosingValues(String missing) {
    String[] settings = Arrays.stream(VALID_SETTINGS)
      .filter(setting -> !setting.startsWith(missing + "="))
      .toArray(String[]::new);
    production()
      .withPropertyValues(settings)
      .run(context -> {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasStackTraceContaining("Production configuration");
        assertThat(context.getStartupFailure().toString()).doesNotContain(
          "private-password-sentinel",
          "private-key-sentinel"
        );
      });
  }

  @ParameterizedTest
  @ValueSource(
    strings = {
      "app.security.session.secure=false",
      "app.security.oauth2.signing.allow-generated-key=true",
      "app.security.session.audit-hmac-secret=short",
      "app.security.oauth2.clients.nextjs.client-secret=local-development-nextjs-client-secret",
      "app.security.oauth2.issuer=http://localhost:8080",
      "app.security.oauth2.issuer=https://user:private-password-sentinel@admin.example.test",
      "app.security.oauth2.clients.nextjs.redirect-uris[1]=http://localhost:3000/callback",
    }
  )
  void unsafeOverridesStopProductionStartup(String override) {
    production()
      .withPropertyValues(VALID_SETTINGS)
      .withPropertyValues(override)
      .run(context -> {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasStackTraceContaining("Production configuration");
        assertThat(context.getStartupFailure().toString()).doesNotContain(
          "private-password-sentinel"
        );
      });
  }

  @Test
  void developmentDoesNotRequireProductionOnlySettings() {
    new ApplicationContextRunner()
      .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
      .withUserConfiguration(ProductionConfiguration.class)
      .run(context -> assertThat(context).hasNotFailed());
  }
}
