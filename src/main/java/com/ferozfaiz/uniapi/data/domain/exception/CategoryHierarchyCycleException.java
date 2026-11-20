package com.ferozfaiz.uniapi.data.domain.exception;

public class CategoryHierarchyCycleException extends RuntimeException {

  private final String messageKey;

  public CategoryHierarchyCycleException() {
    super("Category cannot be moved under itself or one of its descendants");
    this.messageKey = "data.domain.category.hierarchy.cycle";
  }

  public String messageKey() {
    return messageKey;
  }
}
