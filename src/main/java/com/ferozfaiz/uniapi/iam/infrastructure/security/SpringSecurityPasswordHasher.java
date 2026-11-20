package com.ferozfaiz.uniapi.iam.infrastructure.security;

import com.ferozfaiz.uniapi.iam.application.port.PasswordHasher;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityPasswordHasher implements PasswordHasher {

  private final PasswordEncoder passwordEncoder;

  public SpringSecurityPasswordHasher(PasswordHashingProperties properties) {
    this.passwordEncoder = createPasswordEncoder(properties.getDefaultEncoder());
  }

  @Override
  public String hash(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    return passwordEncoder.matches(rawPassword, passwordHash);
  }

  private static PasswordEncoder createPasswordEncoder(String defaultEncoder) {
    String idForEncode = normalize(defaultEncoder);
    Map<String, PasswordEncoder> encoders = supportedEncoders();
    if (!encoders.containsKey(idForEncode)) {
      throw new IllegalStateException("Unsupported password encoder: " + defaultEncoder);
    }

    DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(idForEncode, encoders);
    encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
    return encoder;
  }

  private static Map<String, PasswordEncoder> supportedEncoders() {
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put("bcrypt", new BCryptPasswordEncoder());
    encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());
    return encoders;
  }

  private static String normalize(String encoder) {
    if (encoder == null || encoder.trim().isEmpty()) {
      return "bcrypt";
    }
    return encoder.trim().toLowerCase(Locale.ROOT);
  }
}
