package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SessionCookiePropertiesTest {

  @Test
  void defaultsUseDistinctSessionAndCsrfNames() {
    SessionCookieProperties properties = new SessionCookieProperties();

    assertThat(properties.userCookieName()).isEqualTo("TGP_USER_SESSION");
    assertThat(properties.adminCookieName()).isEqualTo("TGP_ADMIN_SESSION");
    assertThat(properties.userCsrfHeaderName()).isEqualTo("X-TGP-USER-XSRF-TOKEN");
    assertThat(properties.adminCsrfHeaderName()).isEqualTo("X-TGP-ADMIN-XSRF-TOKEN");
    assertThat(properties.hasDistinctSessionCookieNames()).isTrue();
    assertThat(properties.hasDistinctCsrfCookieNames()).isTrue();
    assertThat(properties.hasDistinctCsrfHeaderNames()).isTrue();
  }

  @Test
  void bindsConfiguredValues() throws BindException {
    MapConfigurationPropertySource source = new MapConfigurationPropertySource();
    source.put("app.security.session.user-cookie-name", "USER");
    source.put("app.security.session.admin-cookie-name", "ADMIN");
    source.put("app.security.session.secure", "false");
    source.put("app.security.session.audit-hmac-secret", "test-secret");

    SessionCookieProperties properties = new Binder(source)
      .bind("app.security.session", SessionCookieProperties.class)
      .get();

    assertThat(properties.userCookieName()).isEqualTo("USER");
    assertThat(properties.adminCookieName()).isEqualTo("ADMIN");
    assertThat(properties.secure()).isFalse();
    assertThat(properties.auditHmacSecret()).isEqualTo("test-secret");
  }
}
