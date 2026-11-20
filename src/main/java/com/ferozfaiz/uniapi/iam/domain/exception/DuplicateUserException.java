package com.ferozfaiz.uniapi.iam.domain.exception;

public class DuplicateUserException extends RuntimeException {

  private static final String DUPLICATE_USERNAME_KEY = "iam.domain.user.duplicate.username";
  private static final String DUPLICATE_USERNAME_MESSAGE = "Username is already registered";
  private static final String DUPLICATE_EMAIL_KEY = "iam.domain.user.duplicate.email";
  private static final String DUPLICATE_EMAIL_MESSAGE = "Email is already registered";

  private final String messageKey;

  private DuplicateUserException(String messageKey, String defaultMessage) {
    super(defaultMessage);
    this.messageKey = messageKey;
  }

  public static DuplicateUserException usernameAlreadyRegistered() {
    return new DuplicateUserException(DUPLICATE_USERNAME_KEY, DUPLICATE_USERNAME_MESSAGE);
  }

  public static DuplicateUserException emailAlreadyRegistered() {
    return new DuplicateUserException(DUPLICATE_EMAIL_KEY, DUPLICATE_EMAIL_MESSAGE);
  }

  public String messageKey() {
    return messageKey;
  }
}
