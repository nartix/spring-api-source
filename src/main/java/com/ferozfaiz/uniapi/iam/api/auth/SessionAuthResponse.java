package com.ferozfaiz.uniapi.iam.api.auth;

public record SessionAuthResponse(boolean success) {
  public static SessionAuthResponse ok() {
    return new SessionAuthResponse(true);
  }
}
