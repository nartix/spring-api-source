package com.ferozfaiz.uniapi.data.domain.exception;

public class DuplicateCategoryException extends RuntimeException {

  private final String messageKey;

  private DuplicateCategoryException(String messageKey, String defaultMessage) {
    super(defaultMessage);
    this.messageKey = messageKey;
  }

  public static DuplicateCategoryException slugAlreadyExistsInParent() {
    return new DuplicateCategoryException(
      "data.domain.category.duplicate.slug",
      "Category slug already exists in this parent"
    );
  }

  public String messageKey() {
    return messageKey;
  }
}
