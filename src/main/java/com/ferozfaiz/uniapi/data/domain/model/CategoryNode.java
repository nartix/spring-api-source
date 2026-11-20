package com.ferozfaiz.uniapi.data.domain.model;

import java.util.Objects;

public record CategoryNode(Category category, int depth) {
  public CategoryNode {
    Objects.requireNonNull(category, "category must not be null");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be zero or greater");
    }
  }
}
