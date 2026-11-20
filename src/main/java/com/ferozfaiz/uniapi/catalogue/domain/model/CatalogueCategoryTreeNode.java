package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.util.Objects;

public record CatalogueCategoryTreeNode(
  CatalogueCategory category,
  int depth,
  boolean hasChildren
) {
  public CatalogueCategoryTreeNode {
    Objects.requireNonNull(category, "category must not be null");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be zero or greater");
    }
  }
}
