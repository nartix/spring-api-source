package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.util.Objects;

public record CatalogueCategoryBreadcrumbItem(CatalogueCategory category, int depth) {
  public CatalogueCategoryBreadcrumbItem {
    Objects.requireNonNull(category, "category must not be null");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be zero or greater");
    }
  }
}
