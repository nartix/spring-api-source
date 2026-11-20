package com.ferozfaiz.uniapi.platform.security.authorizationserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class AuthorizationServerTokenConfigurationTest {

  @Test
  void clientCredentialsAccessTokenGetsAudienceAndClientIdWithoutUserClaims() {
    var properties = new OAuthSecurityProperties();
    var customizer = new AuthorizationServerTokenConfiguration().jwtTokenCustomizer(properties);
    var registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId("uniapi-nextjs-server")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .scope("api")
      .build();
    var context = JwtEncodingContext.with(
      JwsHeader.with(SignatureAlgorithm.RS256),
      JwtClaimsSet.builder()
    )
      .registeredClient(registeredClient)
      .principal(
        UsernamePasswordAuthenticationToken.authenticated("uniapi-nextjs-server", null, List.of())
      )
      .authorizedScopes(Set.of("api"))
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .tokenType(OAuth2TokenType.ACCESS_TOKEN)
      .build();

    customizer.customize(context);

    var claims = context.getClaims().build();
    assertThat(claims.getAudience()).containsExactly("uniapi");
    assertThat(claims.getClaimAsString("client_id")).isEqualTo("uniapi-nextjs-server");
    assertThat(claims.getClaims()).doesNotContainKeys("uid", "username", "roles", "permissions");
  }
}
