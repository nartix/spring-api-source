package com.ferozfaiz.uniapi.platform.security.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class OAuthTokenAudienceValidatorTest {

  private final OAuthTokenAudienceValidator validator = new OAuthTokenAudienceValidator("uniapi");

  @Test
  void acceptsExpectedAudience() {
    OAuth2TokenValidatorResult result = validator.validate(jwt(List.of("uniapi")));

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void rejectsMissingAudience() {
    OAuth2TokenValidatorResult result = validator.validate(jwt(List.of("other-api")));

    assertThat(result.hasErrors()).isTrue();
  }

  private static Jwt jwt(List<String> audience) {
    return Jwt.withTokenValue("token")
      .header("alg", "RS256")
      .subject("1")
      .issuer("http://localhost:8080")
      .audience(audience)
      .issuedAt(Instant.parse("2026-05-04T00:00:00Z"))
      .expiresAt(Instant.parse("2026-05-04T00:15:00Z"))
      .build();
  }
}
