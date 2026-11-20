package com.ferozfaiz.uniapi.platform.security.jwk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.platform.security.OAuthSecurityProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class OAuthSigningKeyConfigurationTest {

  private final OAuthSigningKeyConfiguration configuration = new OAuthSigningKeyConfiguration();

  @Test
  void acceptsPemValuesWithEscapedNewlinesFromPropertiesFile() throws Exception {
    KeyPair keyPair = generateKeyPair();
    OAuthSecurityProperties properties = new OAuthSecurityProperties();
    properties.getSigning().setKeyId("test-key");
    properties.getSigning().setPublicKeyPem(publicKeyPem(keyPair).replace("\n", "\\n"));
    properties.getSigning().setPrivateKeyPem(privateKeyPem(keyPair).replace("\n", "\\n"));

    assertThat(configuration.jwkSource(properties)).isNotNull();
  }

  @Test
  void acceptsBase64EncodedPemValuesFromVaultPropertiesFile() throws Exception {
    KeyPair keyPair = generateKeyPair();
    OAuthSecurityProperties properties = new OAuthSecurityProperties();
    properties.getSigning().setKeyId("test-key");
    properties.getSigning().setPublicKeyPemBase64(base64(publicKeyPem(keyPair)));
    properties.getSigning().setPrivateKeyPemBase64(base64(privateKeyPem(keyPair)));

    assertThat(configuration.jwkSource(properties)).isNotNull();
  }

  private static KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String publicKeyPem(KeyPair keyPair) {
    return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
  }

  private static String privateKeyPem(KeyPair keyPair) {
    return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
  }

  private static String pem(String type, byte[] encoded) {
    return (
      "-----BEGIN " +
      type +
      "-----\n" +
      Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded) +
      "\n-----END " +
      type +
      "-----"
    );
  }

  private static String base64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes());
  }
}
