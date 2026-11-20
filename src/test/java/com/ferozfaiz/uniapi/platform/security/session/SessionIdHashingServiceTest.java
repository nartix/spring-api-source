package com.ferozfaiz.uniapi.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionIdHashingServiceTest {

  @Test
  void hashesSessionIdWithConfiguredHmacSecret() {
    SessionCookieProperties properties = new SessionCookieProperties();
    properties.setAuditHmacSecret("local-test-secret");

    String hash = new SessionIdHashingService(properties).hash("raw-session-id");

    assertThat(hash).hasSize(64);
    assertThat(hash).doesNotContain("raw-session-id");
    assertThat(hash).isEqualTo(new SessionIdHashingService(properties).hash("raw-session-id"));
  }

  @Test
  void refusesToHashWithoutSecret() {
    SessionCookieProperties properties = new SessionCookieProperties();

    assertThatThrownBy(() -> new SessionIdHashingService(properties).hash("raw-session-id"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("HMAC secret");
  }

  @Test
  void returnsNullForMissingSessionId() {
    assertThat(new SessionIdHashingService(new SessionCookieProperties()).hash(null)).isNull();
  }
}
