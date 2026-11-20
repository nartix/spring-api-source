package com.ferozfaiz.uniapi.platform.security.authorizationserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AuthorizationServerPersistenceConfigurationTest {

  @Test
  void oauthAuthorizationObjectMapperAllowsAuthenticatedPrincipal() throws Exception {
    var mapper = AuthorizationServerPersistenceConfiguration.oauthAuthorizationObjectMapper();
    var principal = new AuthenticatedPrincipal(1L, "admin", Set.of("ADMIN"), Set.of("users:read"));
    var authentication = UsernamePasswordAuthenticationToken.authenticated(
      principal,
      null,
      List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    );
    var attributes = new HashMap<String, Object>();
    attributes.put(Principal.class.getName(), authentication);

    String json = mapper.writeValueAsString(attributes);
    Map<String, Object> restored = mapper.readValue(json, new TypeReference<>() {});

    var restoredAuthentication = (UsernamePasswordAuthenticationToken) restored.get(
      Principal.class.getName()
    );
    assertThat(restoredAuthentication.getPrincipal()).isEqualTo(principal);
  }

  @Test
  void persistedJwtMetadataSupportsJdkImmutableAudienceLists() throws Exception {
    var mapper = AuthorizationServerPersistenceConfiguration.oauthAuthorizationObjectMapper();
    for (var audience : List.of(
      List.<String>of(),
      List.of("uniapi"),
      List.of("uniapi", "secondary"),
      List.of("one", "two", "three")
    )) {
      var claims = new HashMap<String, Object>();
      claims.put("aud", audience);
      var metadata = new HashMap<String, Object>();
      metadata.put("metadata.token.claims", claims);
      Map<String, Object> restored = mapper.readValue(
        mapper.writeValueAsString(metadata),
        new TypeReference<>() {}
      );
      var restoredClaims = (Map<?, ?>) restored.get("metadata.token.claims");
      assertThat(restoredClaims.get("aud")).isEqualTo(audience);
    }
  }

  @Test
  void securityMapperStillRejectsUntrustedPolymorphicTypes() throws Exception {
    var mapper = AuthorizationServerPersistenceConfiguration.oauthAuthorizationObjectMapper();
    var metadata = new HashMap<String, Object>();
    metadata.put("unexpected", new UntrustedPayload("synthetic"));
    String json = mapper.writeValueAsString(metadata);
    assertThatThrownBy(() ->
      mapper.readValue(json, new TypeReference<Map<String, Object>>() {})
    ).hasStackTraceContaining("not in the allowlist");
  }

  private record UntrustedPayload(String value) {}
}
