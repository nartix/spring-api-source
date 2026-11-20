package com.ferozfaiz.uniapi.data.domain.exception;

public class CategoryHasChildrenException extends RuntimeException {

  private final String messageKey;

  public CategoryHasChildrenException(Long id) {
    super("Category has children: " + id);
    this.messageKey = "data.domain.category.has-children";
  }

  public String messageKey() {
    return messageKey;
  }
}
