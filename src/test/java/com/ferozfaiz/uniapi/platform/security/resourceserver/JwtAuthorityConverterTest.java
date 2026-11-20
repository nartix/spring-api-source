package com.ferozfaiz.uniapi.platform.security.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthorityConverterTest {

  private final JwtAuthorityConverter converter = new JwtAuthorityConverter();

  @Test
  void mapsScopesRolesAndPermissions() {
    assertThat(converter.convert(jwt()))
      .extracting("authority")
      .contains("SCOPE_api", "ROLE_ADMIN", "users:read", "users:write");
  }

  @Test
  void mapsStringRoleAndPermissionClaims() {
    assertThat(
      converter.convert(
        Jwt.withTokenValue("token")
          .header("alg", "RS256")
          .subject("1")
          .claim("scope", "api")
          .claim("roles", "ADMIN")
          .claim("permissions", "users:read")
          .issuedAt(Instant.parse("2026-05-04T00:00:00Z"))
          .expiresAt(Instant.parse("2026-05-04T00:15:00Z"))
          .build()
      )
    )
      .extracting("authority")
      .contains("SCOPE_api", "ROLE_ADMIN", "users:read");
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue("token")
      .header("alg", "RS256")
      .subject("1")
      .claim("username", "feroz")
      .claim("scope", "api")
      .claim("roles", List.of("ADMIN"))
      .claim("permissions", List.of("users:read", "users:write"))
      .issuedAt(Instant.parse("2026-05-04T00:00:00Z"))
      .expiresAt(Instant.parse("2026-05-04T00:15:00Z"))
      .build();
  }
}
