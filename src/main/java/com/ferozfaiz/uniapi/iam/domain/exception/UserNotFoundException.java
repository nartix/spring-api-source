package com.ferozfaiz.uniapi.iam.domain.exception;

public class UserNotFoundException extends RuntimeException {

  private final String messageKey;

  public UserNotFoundException(Long id) {
    super("User not found: " + id);
    this.messageKey = "iam.domain.user.not-found";
  }

  public String messageKey() {
    return messageKey;
  }
}
