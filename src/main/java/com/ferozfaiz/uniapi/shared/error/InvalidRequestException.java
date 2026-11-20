package com.ferozfaiz.uniapi.shared.error;

import com.ferozfaiz.uniapi.shared.i18n.LocalizedMessage;

public class InvalidRequestException extends IllegalArgumentException implements LocalizedMessage {

  private final String messageKey;
  private final Object[] messageArgs;

  public InvalidRequestException(String messageKey, String defaultMessage, Object... messageArgs) {
    super(defaultMessage);
    this.messageKey = messageKey;
    this.messageArgs = messageArgs.clone();
  }

  public static InvalidRequestException requiredField(String fieldName) {
    return new InvalidRequestException(
      "shared.request.field-required",
      fieldName + " must not be blank",
      fieldName
    );
  }

  @Override
  public String messageKey() {
    return messageKey;
  }

  @Override
  public Object[] messageArgs() {
    return messageArgs.clone();
  }
}
