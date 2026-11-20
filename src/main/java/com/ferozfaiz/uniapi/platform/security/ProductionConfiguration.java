package com.ferozfaiz.uniapi.platform.security;

import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {

  @Bean
  static BeanFactoryPostProcessor productionSettingsGuard(Environment environment) {
    return beanFactory -> validate(environment);
  }

  private static void validate(Environment environment) {
    requireText(environment, "spring.datasource.url");
    requireText(environment, "spring.datasource.username");
    requireText(environment, "spring.datasource.password");
    requireValue(environment, "app.security.session.secure", "true");
    requireValue(environment, "app.security.oauth2.signing.allow-generated-key", "false");
    requireSecret(environment, "app.security.session.audit-hmac-secret");
    requireSecret(environment, "app.security.oauth2.clients.nextjs.client-secret");
    requireSecret(environment, "app.security.oauth2.clients.nextjs-server.client-secret");
    requireHttps(
      requireText(environment, "app.security.oauth2.issuer"),
      "app.security.oauth2.issuer"
    );

    String signing = "app.security.oauth2.signing.";
    if ("local-dev".equals(requireText(environment, signing + "key-id"))) {
      throw invalid(signing + "key-id");
    }
    requireKey(environment, signing + "public-key-pem");
    requireKey(environment, signing + "private-key-pem");
    requireHttpsList(environment, "app.security.oauth2.clients.nextjs.redirect-uris");
    requireHttpsList(environment, "app.security.oauth2.clients.nextjs.post-logout-redirect-uris");
  }

  private static void requireHttpsList(Environment environment, String name) {
    List<String> urls;
    try {
      urls = Binder.get(environment).bind(name, Bindable.listOf(String.class)).orElse(List.of());
    } catch (RuntimeException exception) {
      throw invalid(name);
    }
    if (urls.isEmpty()) {
      throw invalid(name);
    }
    urls.forEach(url -> requireHttps(url, name));
  }

  private static void requireHttps(String value, String name) {
    try {
      URI uri = URI.create(value);
      String host = uri.getHost();
      if (
        !"https".equals(uri.getScheme()) ||
        host == null ||
        uri.getUserInfo() != null ||
        uri.getRawQuery() != null ||
        uri.getRawFragment() != null ||
        "localhost".equalsIgnoreCase(host) ||
        host.toLowerCase().endsWith(".localhost") ||
        host.startsWith("127.") ||
        "[::1]".equals(host)
      ) {
        throw invalid(name);
      }
    } catch (RuntimeException exception) {
      // URI parser exceptions can quote input. Never retain them as causes.
      throw invalid(name);
    }
  }

  private static void requireKey(Environment environment, String name) {
    if (read(environment, name).isBlank() && read(environment, name + "-base64").isBlank()) {
      throw invalid(name);
    }
  }

  private static void requireSecret(Environment environment, String name) {
    String value = requireText(environment, name);
    if (value.length() < 32 || value.startsWith("local-development-")) {
      throw invalid(name);
    }
  }

  private static void requireValue(Environment environment, String name, String expected) {
    if (!expected.equalsIgnoreCase(requireText(environment, name))) {
      throw invalid(name);
    }
  }

  private static String requireText(Environment environment, String name) {
    String value = read(environment, name);
    if (value.isBlank()) {
      throw invalid(name);
    }
    return value;
  }

  private static String read(Environment environment, String name) {
    try {
      return environment.getProperty(name, "").strip();
    } catch (RuntimeException exception) {
      // Unresolved placeholders can quote surrounding configuration values.
      throw invalid(name);
    }
  }

  private static IllegalStateException invalid(String name) {
    return new IllegalStateException("Production configuration is invalid or missing: " + name);
  }
}
