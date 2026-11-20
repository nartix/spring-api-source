package com.ferozfaiz.uniapi.platform.security.session;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SessionIdHashingService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SessionCookieProperties properties;

  public SessionIdHashingService(SessionCookieProperties properties) {
    this.properties = properties;
  }

  public String hash(String sessionId) {
    if (!StringUtils.hasText(sessionId)) {
      return null;
    }
    if (!StringUtils.hasText(properties.auditHmacSecret())) {
      throw new IllegalStateException("Session audit HMAC secret must be configured");
    }

    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(
        new SecretKeySpec(
          properties.auditHmacSecret().getBytes(StandardCharsets.UTF_8),
          HMAC_ALGORITHM
        )
      );
      return HexFormat.of().formatHex(mac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash session identifier", exception);
    }
  }
}
