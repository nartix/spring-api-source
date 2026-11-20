package com.ferozfaiz.uniapi.catalogue.domain.exception;

import com.ferozfaiz.uniapi.shared.i18n.LocalizedMessage;

public class CatalogueException extends RuntimeException implements LocalizedMessage {

  private final String messageKey;
  private final Object[] messageArgs;

  public CatalogueException(String messageKey, String defaultMessage, Object... messageArgs) {
    super(defaultMessage);
    this.messageKey = messageKey;
    this.messageArgs = messageArgs.clone();
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
