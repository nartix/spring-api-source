package com.ferozfaiz.uniapi.platform.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.oauth2")
public class OAuthSecurityProperties {

  private String issuer = "http://localhost:8080";
  private String audience = "uniapi";
  private Signing signing = new Signing();
  private Clients clients = new Clients();
  private Tokens tokens = new Tokens();

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public Signing getSigning() {
    return signing;
  }

  public void setSigning(Signing signing) {
    this.signing = signing;
  }

  public Clients getClients() {
    return clients;
  }

  public void setClients(Clients clients) {
    this.clients = clients;
  }

  public Tokens getTokens() {
    return tokens;
  }

  public void setTokens(Tokens tokens) {
    this.tokens = tokens;
  }

  public static class Signing {

    private String keyId = "local-dev";
    private String publicKeyPem;
    private String privateKeyPem;
    private String publicKeyPemBase64;
    private String privateKeyPemBase64;
    private boolean allowGeneratedKey;

    public String getKeyId() {
      return keyId;
    }

    public void setKeyId(String keyId) {
      this.keyId = keyId;
    }

    public String getPublicKeyPem() {
      return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
      this.publicKeyPem = publicKeyPem;
    }

    public String getPrivateKeyPem() {
      return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
      this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPemBase64() {
      return publicKeyPemBase64;
    }

    public void setPublicKeyPemBase64(String publicKeyPemBase64) {
      this.publicKeyPemBase64 = publicKeyPemBase64;
    }

    public String getPrivateKeyPemBase64() {
      return privateKeyPemBase64;
    }

    public void setPrivateKeyPemBase64(String privateKeyPemBase64) {
      this.privateKeyPemBase64 = privateKeyPemBase64;
    }

    public boolean isAllowGeneratedKey() {
      return allowGeneratedKey;
    }

    public void setAllowGeneratedKey(boolean allowGeneratedKey) {
      this.allowGeneratedKey = allowGeneratedKey;
    }
  }

  public static class Clients {

    private Nextjs nextjs = new Nextjs();
    private NextjsServer nextjsServer = new NextjsServer();

    public Nextjs getNextjs() {
      return nextjs;
    }

    public void setNextjs(Nextjs nextjs) {
      this.nextjs = nextjs;
    }

    public NextjsServer getNextjsServer() {
      return nextjsServer;
    }

    public void setNextjsServer(NextjsServer nextjsServer) {
      this.nextjsServer = nextjsServer;
    }
  }

  public static class Nextjs {

    private String clientId = "uniapi-nextjs";
    private String clientSecret;
    private List<String> redirectUris = new ArrayList<>(
      List.of("http://localhost:3000/api/auth/callback/uniapi")
    );
    private List<String> postLogoutRedirectUris = new ArrayList<>(List.of("http://localhost:3000"));
    private boolean requireAuthorizationConsent = true;
    private boolean requireProofKey = true;

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public List<String> getRedirectUris() {
      return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
      this.redirectUris = redirectUris;
    }

    public List<String> getPostLogoutRedirectUris() {
      return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
      this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public boolean isRequireAuthorizationConsent() {
      return requireAuthorizationConsent;
    }

    public void setRequireAuthorizationConsent(boolean requireAuthorizationConsent) {
      this.requireAuthorizationConsent = requireAuthorizationConsent;
    }

    public boolean isRequireProofKey() {
      return requireProofKey;
    }

    public void setRequireProofKey(boolean requireProofKey) {
      this.requireProofKey = requireProofKey;
    }
  }

  public static class NextjsServer {

    private String clientId = "uniapi-nextjs-server";
    private String clientSecret;
    private List<String> scopes = new ArrayList<>(
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

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public List<String> getScopes() {
      return scopes;
    }

    public void setScopes(List<String> scopes) {
      this.scopes =
        scopes == null
          ? new ArrayList<>()
          : scopes
              .stream()
              .filter(scope -> scope != null && !scope.isBlank() && !"<no value>".equals(scope))
              .distinct()
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
      if (this.scopes.isEmpty()) {
        this.scopes.add("api");
      }
    }
  }

  public static class Tokens {

    private Duration accessTokenTimeToLive = Duration.ofMinutes(15);
    private Duration refreshTokenTimeToLive = Duration.ofDays(30);
    private boolean reuseRefreshTokens;

    public Duration getAccessTokenTimeToLive() {
      return accessTokenTimeToLive;
    }

    public void setAccessTokenTimeToLive(Duration accessTokenTimeToLive) {
      this.accessTokenTimeToLive = accessTokenTimeToLive;
    }

    public Duration getRefreshTokenTimeToLive() {
      return refreshTokenTimeToLive;
    }

    public void setRefreshTokenTimeToLive(Duration refreshTokenTimeToLive) {
      this.refreshTokenTimeToLive = refreshTokenTimeToLive;
    }

    public boolean isReuseRefreshTokens() {
      return reuseRefreshTokens;
    }

    public void setReuseRefreshTokens(boolean reuseRefreshTokens) {
      this.reuseRefreshTokens = reuseRefreshTokens;
    }
  }
}
