package com.ferozfaiz.uniapi.iam.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.password")
public class PasswordHashingProperties {

  private String defaultEncoder = "bcrypt";

  public String getDefaultEncoder() {
    return defaultEncoder;
  }

  public void setDefaultEncoder(String defaultEncoder) {
    this.defaultEncoder = defaultEncoder;
  }
}
