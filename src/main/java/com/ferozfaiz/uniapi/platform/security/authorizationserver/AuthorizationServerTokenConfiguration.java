package com.ferozfaiz.uniapi.platform.security.authorizationserver;

import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class AuthorizationServerTokenConfiguration {

  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(OAuthSecurityProperties properties) {
    return context -> {
      if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        return;
      }

      context.getClaims().audience(List.of(properties.getAudience()));
      if (context.getRegisteredClient() != null) {
        context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
      }
      if (context.getPrincipal().getPrincipal() instanceof AuthenticatedPrincipal principal) {
        context.getClaims().claim("uid", principal.userId());
        context.getClaims().claim("username", principal.username());
        context.getClaims().claim("roles", principal.roles());
        context.getClaims().claim("permissions", principal.permissions());
      }
    };
  }
}
