package com.ferozfaiz.uniapi.platform.security.jwk;

import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import com.ferozfaiz.uniapi.platform.security.resourceserver.OAuthTokenAudienceValidator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;

@Configuration
public class OAuthSigningKeyConfiguration {

  @Bean
  JWKSource<SecurityContext> jwkSource(OAuthSecurityProperties properties) {
    OAuthSecurityProperties.Signing signing = properties.getSigning();
    RSAKey rsaKey = rsaKey(signing);
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  @Bean
  JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, OAuthSecurityProperties properties) {
    NimbusJwtDecoder jwtDecoder =
      (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
      JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
      new OAuthTokenAudienceValidator(properties.getAudience())
    );
    jwtDecoder.setJwtValidator(validators);
    return jwtDecoder;
  }

  private static RSAKey rsaKey(OAuthSecurityProperties.Signing signing) {
    if (hasText(resolvePrivateKeyPem(signing)) && hasText(resolvePublicKeyPem(signing))) {
      return configuredRsaKey(signing);
    }
    if (signing.isAllowGeneratedKey()) {
      return generatedRsaKey(signing.getKeyId());
    }
    throw new IllegalStateException(
      "OAuth2 signing public/private key PEM values must be configured"
    );
  }

  private static RSAKey configuredRsaKey(OAuthSecurityProperties.Signing signing) {
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
        new X509EncodedKeySpec(decodePem(resolvePublicKeyPem(signing)))
      );
      RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
        new PKCS8EncodedKeySpec(decodePem(resolvePrivateKeyPem(signing)))
      );
      return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(resolveKeyId(signing.getKeyId()))
        .build();
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid OAuth2 RSA signing key configuration", exception);
    }
  }

  private static RSAKey generatedRsaKey(String keyId) {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
        .privateKey((RSAPrivateKey) keyPair.getPrivate())
        .keyID(resolveKeyId(keyId))
        .build();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not generate local OAuth2 RSA signing key", exception);
    }
  }

  private static byte[] decodePem(String pem) {
    String normalized = pem
      .replace("\\r", "")
      .replace("\\n", "")
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replace("-----END PUBLIC KEY-----", "")
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replaceAll("\\s", "");
    return Base64.getDecoder().decode(normalized);
  }

  private static String resolvePublicKeyPem(OAuthSecurityProperties.Signing signing) {
    return resolvePem(signing.getPublicKeyPem(), signing.getPublicKeyPemBase64());
  }

  private static String resolvePrivateKeyPem(OAuthSecurityProperties.Signing signing) {
    return resolvePem(signing.getPrivateKeyPem(), signing.getPrivateKeyPemBase64());
  }

  private static String resolvePem(String pem, String pemBase64) {
    if (hasText(pemBase64)) {
      return new String(Base64.getDecoder().decode(pemBase64));
    }
    return pem;
  }

  private static String resolveKeyId(String keyId) {
    return hasText(keyId) ? keyId.trim() : UUID.randomUUID().toString();
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
