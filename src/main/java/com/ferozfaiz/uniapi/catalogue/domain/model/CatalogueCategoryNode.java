package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.util.Objects;

public record CatalogueCategoryNode(CatalogueCategory category, int depth, boolean hasChildren) {
  public CatalogueCategoryNode {
    Objects.requireNonNull(category, "category must not be null");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be zero or greater");
    }
  }
}
