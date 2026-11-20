package com.ferozfaiz.uniapi.platform.security.authorizationserver;

import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

public class OAuthRegisteredClientSeeder implements ApplicationRunner {

  private static final Set<String> UNSAFE_CLIENT_SECRET_VALUES = Set.of(
    "{noop}change-me",
    "dev-nextjs-server-client-secret-change-me",
    "change-me",
    "<no value>"
  );

  private final RegisteredClientRepository clients;
  private final PasswordEncoder passwordEncoder;
  private final OAuthSecurityProperties properties;

  public OAuthRegisteredClientSeeder(
    RegisteredClientRepository clients,
    PasswordEncoder passwordEncoder,
    OAuthSecurityProperties properties
  ) {
    this.clients = clients;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    seedNextjsClient();
    seedNextjsServerClient();
  }

  void seedNextjsClient() {
    OAuthSecurityProperties.Nextjs nextjs = properties.getClients().getNextjs();
    validateClientSecret(nextjs.getClientSecret());
    RegisteredClient existing = clients.findByClientId(nextjs.getClientId());
    if (existing == null) {
      clients.save(nextjsClient(nextjs));
      return;
    }
    reconcileScopes(
      existing,
      new LinkedHashSet<>(List.of(OidcScopes.OPENID, OidcScopes.PROFILE, "api"))
    );
  }

  void seedNextjsServerClient() {
    OAuthSecurityProperties.NextjsServer nextjsServer = properties.getClients().getNextjsServer();
    validateClientSecret(nextjsServer.getClientSecret());
    RegisteredClient existing = clients.findByClientId(nextjsServer.getClientId());
    if (existing == null) {
      clients.save(nextjsServerClient(nextjsServer));
      return;
    }
    reconcileScopes(existing, new LinkedHashSet<>(nextjsServer.getScopes()));
  }

  private void reconcileScopes(RegisteredClient existing, Set<String> desiredScopes) {
    if (existing.getScopes().equals(desiredScopes)) {
      return;
    }
    RegisteredClient updated = RegisteredClient.from(existing)
      .scopes(scopes -> {
        scopes.clear();
        scopes.addAll(desiredScopes);
      })
      .build();
    clients.save(updated);
  }

  private RegisteredClient nextjsClient(OAuthSecurityProperties.Nextjs nextjs) {
    RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId(nextjs.getClientId())
      .clientIdIssuedAt(Instant.now())
      .clientSecret(clientSecret(nextjs.getClientSecret()))
      .clientName("UniAPI Next.js")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
      .scope(OidcScopes.OPENID)
      .scope(OidcScopes.PROFILE)
      .scope("api")
      .clientSettings(
        ClientSettings.builder()
          .requireAuthorizationConsent(nextjs.isRequireAuthorizationConsent())
          .requireProofKey(nextjs.isRequireProofKey())
          .build()
      )
      .tokenSettings(
        TokenSettings.builder()
          .accessTokenTimeToLive(properties.getTokens().getAccessTokenTimeToLive())
          .refreshTokenTimeToLive(properties.getTokens().getRefreshTokenTimeToLive())
          .reuseRefreshTokens(properties.getTokens().isReuseRefreshTokens())
          .build()
      );

    nextjs.getRedirectUris().forEach(builder::redirectUri);
    nextjs.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
    return builder.build();
  }

  private RegisteredClient nextjsServerClient(OAuthSecurityProperties.NextjsServer nextjsServer) {
    RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
      .clientId(nextjsServer.getClientId())
      .clientIdIssuedAt(Instant.now())
      .clientSecret(clientSecret(nextjsServer.getClientSecret()))
      .clientName("UniAPI Next.js Server")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .clientSettings(ClientSettings.builder().build())
      .tokenSettings(
        TokenSettings.builder()
          .accessTokenTimeToLive(properties.getTokens().getAccessTokenTimeToLive())
          .build()
      );

    nextjsServer.getScopes().forEach(builder::scope);
    return builder.build();
  }

  private String clientSecret(String configuredSecret) {
    String secret = validateClientSecret(configuredSecret);
    if (secret.startsWith("{bcrypt}")) {
      return secret;
    }
    return passwordEncoder.encode(secret);
  }

  private static String validateClientSecret(String configuredSecret) {
    String secret = configuredSecret == null ? "" : configuredSecret.trim();
    if (secret.isEmpty() || UNSAFE_CLIENT_SECRET_VALUES.contains(secret)) {
      throw new IllegalStateException(
        "OAuth client secret must be configured with a non-placeholder value"
      );
    }
    if (secret.startsWith("{bcrypt}") && secret.length() > "{bcrypt}".length()) {
      return secret;
    }
    if (secret.startsWith("{")) {
      throw new IllegalStateException("OAuth client secret must be raw or bcrypt encoded");
    }
    return secret;
  }
}
