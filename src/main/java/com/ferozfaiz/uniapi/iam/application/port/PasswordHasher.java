package com.ferozfaiz.uniapi.iam.application.port;

public interface PasswordHasher {
  String hash(String rawPassword);

  boolean matches(String rawPassword, String passwordHash);
}
