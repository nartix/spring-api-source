package com.ferozfaiz.uniapi.platform.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;

class AuthenticatedPrincipalTest {

  @Test
  void serializesInsideSecurityContextForJdbcSessionStorage() {
    var principal = new AuthenticatedPrincipal(1L, "admin", Set.of("ADMIN"), Set.of("users:read"));
    var authentication = UsernamePasswordAuthenticationToken.authenticated(
      principal,
      null,
      List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    );
    var securityContext = new SecurityContextImpl(authentication);

    byte[] serialized = new SerializingConverter().convert(securityContext);

    assertThat(serialized).isNotEmpty();
  }
}
