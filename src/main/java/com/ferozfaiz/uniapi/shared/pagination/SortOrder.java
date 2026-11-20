package com.ferozfaiz.uniapi.shared.pagination;

public record SortOrder(String property, SortDirection direction) {
  public SortOrder {
    if (property == null || property.isBlank()) {
      throw new IllegalArgumentException("sort property must not be blank");
    }
    if (direction == null) {
      throw new IllegalArgumentException("sort direction is required");
    }
  }
}
