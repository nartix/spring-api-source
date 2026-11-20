package com.ferozfaiz.uniapi.data.domain.exception;

public class CategoryNotFoundException extends RuntimeException {

  private final String messageKey;

  public CategoryNotFoundException(Long id) {
    super("Category not found: " + id);
    this.messageKey = "data.domain.category.not-found";
  }

  public String messageKey() {
    return messageKey;
  }
}
