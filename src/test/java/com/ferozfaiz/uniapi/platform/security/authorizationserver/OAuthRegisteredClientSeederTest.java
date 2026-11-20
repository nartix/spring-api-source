package com.ferozfaiz.uniapi.platform.security.authorizationserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

class OAuthRegisteredClientSeederTest {

  private final FakeRegisteredClientRepository clients = new FakeRegisteredClientRepository();
  private final OAuthSecurityProperties properties = propertiesWithClientSecrets();
  private final OAuthRegisteredClientSeeder seeder = new OAuthRegisteredClientSeeder(
    clients,
    PasswordEncoderFactories.createDelegatingPasswordEncoder(),
    properties
  );

  @Test
  void seedsNextjsClient() {
    seeder.seedNextjsClient();

    RegisteredClient client = clients.findByClientId("uniapi-nextjs");

    assertThat(client).isNotNull();
    assertThat(client.getClientAuthenticationMethods()).contains(
      ClientAuthenticationMethod.CLIENT_SECRET_BASIC
    );
    assertThat(client.getAuthorizationGrantTypes()).contains(
      AuthorizationGrantType.AUTHORIZATION_CODE,
      AuthorizationGrantType.REFRESH_TOKEN
    );
    assertThat(client.getScopes()).contains(OidcScopes.OPENID, OidcScopes.PROFILE, "api");
    assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
    assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
    assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    assertThat(client.getClientSecret()).startsWith("{bcrypt}");
  }

  @Test
  void seedsNextjsServerClientForClientCredentials() {
    seeder.seedNextjsServerClient();

    RegisteredClient client = clients.findByClientId("uniapi-nextjs-server");

    assertThat(client).isNotNull();
    assertThat(client.getClientAuthenticationMethods()).contains(
      ClientAuthenticationMethod.CLIENT_SECRET_BASIC
    );
    assertThat(client.getAuthorizationGrantTypes()).containsExactly(
      AuthorizationGrantType.CLIENT_CREDENTIALS
    );
    assertThat(client.getScopes()).containsExactlyInAnyOrder(
      "api",
      "catalogue:read",
      "catalogue:write",
      "users:read",
      "users:write",
      "categories:read",
      "categories:write"
    );
    assertThat(client.getScopes()).doesNotContain(OidcScopes.OPENID, OidcScopes.PROFILE);
    assertThat(client.getAuthorizationGrantTypes()).doesNotContain(
      AuthorizationGrantType.AUTHORIZATION_CODE,
      AuthorizationGrantType.REFRESH_TOKEN
    );
    assertThat(client.getClientSettings().isRequireProofKey()).isFalse();
    assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
    assertThat(client.getClientSecret()).startsWith("{bcrypt}");
  }

  @Test
  void filtersUnresolvedVaultScopePlaceholdersWithoutExpandingScopes() {
    properties.getClients().getNextjsServer().setScopes(List.of("api", "<no value>"));

    seeder.seedNextjsServerClient();

    RegisteredClient client = clients.findByClientId("uniapi-nextjs-server");

    assertThat(client.getScopes()).containsExactly("api");
  }

  @Test
  void rejectsNoopClientSecret() {
    properties.getClients().getNextjs().setClientSecret("{noop}change-me");

    assertThatThrownBy(seeder::seedNextjsClient)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("OAuth client secret");
  }

  @Test
  void rejectsCommittedServerPlaceholderClientSecret() {
    properties
      .getClients()
      .getNextjsServer()
      .setClientSecret("dev-nextjs-server-client-secret-change-me");

    assertThatThrownBy(seeder::seedNextjsServerClient)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("OAuth client secret");
  }

  @Test
  void rejectsPlaceholderClientSecretEvenWhenClientAlreadyExists() {
    seeder.seedNextjsServerClient();
    properties.getClients().getNextjsServer().setClientSecret("<no value>");

    assertThatThrownBy(seeder::seedNextjsServerClient)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("OAuth client secret");
  }

  @Test
  void preservesConfiguredBcryptClientSecret() {
    String encodedSecret = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(
      "configured-secret"
    );
    properties.getClients().getNextjsServer().setClientSecret(encodedSecret);

    seeder.seedNextjsServerClient();

    RegisteredClient client = clients.findByClientId("uniapi-nextjs-server");
    assertThat(client.getClientSecret()).isEqualTo(encodedSecret);
  }

  @Test
  void seedingIsIdempotent() {
    seeder.run(null);
    seeder.run(null);

    assertThat(clients.savedCount()).isEqualTo(2);
  }

  @Test
  void reconcilesScopesWhenNextjsClientAlreadyExistsWithStaleScopes() {
    seeder.seedNextjsClient();
    RegisteredClient initial = clients.findByClientId("uniapi-nextjs");
    RegisteredClient stale = RegisteredClient.from(initial)
      .scopes(scopes -> {
        scopes.clear();
        scopes.add(OidcScopes.OPENID);
      })
      .build();
    clients.save(stale);
    assertThat(clients.findByClientId("uniapi-nextjs").getScopes()).containsExactly(
      OidcScopes.OPENID
    );

    seeder.seedNextjsClient();

    RegisteredClient updated = clients.findByClientId("uniapi-nextjs");
    assertThat(updated.getScopes()).containsExactly(OidcScopes.OPENID, OidcScopes.PROFILE, "api");
    assertThat(updated.getId()).isEqualTo(initial.getId());
  }

  @Test
  void reconcilesScopesWhenNextjsServerClientAlreadyExistsWithStaleScopes() {
    seeder.seedNextjsServerClient();
    RegisteredClient initial = clients.findByClientId("uniapi-nextjs-server");
    RegisteredClient stale = RegisteredClient.from(initial)
      .scopes(scopes -> {
        scopes.clear();
        scopes.add("api");
      })
      .build();
    clients.save(stale);
    assertThat(clients.findByClientId("uniapi-nextjs-server").getScopes()).containsExactly("api");

    properties
      .getClients()
      .getNextjsServer()
      .setScopes(
        List.of(
          "api",
          "catalogue:read",
          "catalogue:write",
          "users:read",
          "users:write",
          "categories:read",
          "categories:write"
        )
      );
    seeder.seedNextjsServerClient();

    RegisteredClient updated = clients.findByClientId("uniapi-nextjs-server");
    assertThat(updated.getScopes()).containsExactlyInAnyOrder(
      "api",
      "catalogue:read",
      "catalogue:write",
      "users:read",
      "users:write",
      "categories:read",
      "categories:write"
    );
    assertThat(updated.getId()).isEqualTo(initial.getId());
  }

  @Test
  void scopeReconciliationRemovesExistingUnrelatedScopes() {
    seeder.seedNextjsServerClient();
    RegisteredClient initial = clients.findByClientId("uniapi-nextjs-server");
    RegisteredClient custom = RegisteredClient.from(initial)
      .scopes(scopes -> {
        scopes.clear();
        scopes.add("api");
        scopes.add("custom:scope");
      })
      .build();
    clients.save(custom);
    properties.getClients().getNextjsServer().setScopes(List.of("api"));

    seeder.seedNextjsServerClient();

    RegisteredClient updated = clients.findByClientId("uniapi-nextjs-server");
    assertThat(updated.getScopes()).containsExactly("api");
    assertThat(updated.getId()).isEqualTo(initial.getId());
  }

  private static OAuthSecurityProperties propertiesWithClientSecrets() {
    var properties = new OAuthSecurityProperties();
    properties.getClients().getNextjs().setClientSecret("test-nextjs-client-secret");
    properties.getClients().getNextjsServer().setClientSecret("test-nextjs-server-client-secret");
    return properties;
  }

  private static class FakeRegisteredClientRepository implements RegisteredClientRepository {

    private final Map<String, RegisteredClient> clientsById = new LinkedHashMap<>();
    private int savedCount;

    @Override
    public void save(RegisteredClient registeredClient) {
      clientsById.put(registeredClient.getId(), registeredClient);
      savedCount++;
    }

    @Override
    public RegisteredClient findById(String id) {
      return clientsById.get(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
      return clientsById
        .values()
        .stream()
        .filter(client -> client.getClientId().equals(clientId))
        .findFirst()
        .orElse(null);
    }

    int savedCount() {
      return savedCount;
    }
  }
}
