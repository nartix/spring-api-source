package com.ferozfaiz.uniapi.iam.api.auth;

public class AuthenticationFailedException extends RuntimeException {

  private final Reason reason;
  private final String messageKey;

  private AuthenticationFailedException(Reason reason, String messageKey, String message) {
    super(message);
    this.reason = reason;
    this.messageKey = messageKey;
  }

  public static AuthenticationFailedException invalidCredentials() {
    return new AuthenticationFailedException(
      Reason.INVALID_CREDENTIALS,
      "iam.application.auth.invalid-credentials",
      "Invalid credentials"
    );
  }

  public static AuthenticationFailedException disabled() {
    return new AuthenticationFailedException(
      Reason.DISABLED,
      "iam.application.auth.disabled",
      "User is disabled"
    );
  }

  public static AuthenticationFailedException expired() {
    return new AuthenticationFailedException(
      Reason.EXPIRED,
      "iam.application.auth.expired",
      "User account is expired"
    );
  }

  public static AuthenticationFailedException locked() {
    return new AuthenticationFailedException(
      Reason.LOCKED,
      "iam.application.auth.locked",
      "User account is locked"
    );
  }

  public static AuthenticationFailedException credentialsExpired() {
    return new AuthenticationFailedException(
      Reason.CREDENTIALS_EXPIRED,
      "iam.application.auth.credentials-expired",
      "User credentials are expired"
    );
  }

  public Reason reason() {
    return reason;
  }

  public String messageKey() {
    return messageKey;
  }

  public enum Reason {
    INVALID_CREDENTIALS,
    DISABLED,
    EXPIRED,
    LOCKED,
    CREDENTIALS_EXPIRED,
  }
}
