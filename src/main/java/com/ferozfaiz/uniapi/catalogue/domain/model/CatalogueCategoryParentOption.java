package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.util.List;
import java.util.Objects;

public record CatalogueCategoryParentOption(
  CatalogueCategory category,
  int depth,
  String label,
  List<CatalogueCategoryBreadcrumbItem> path,
  boolean selectable,
  CatalogueCategoryParentOptionDisabledReason disabledReason
) {
  public CatalogueCategoryParentOption {
    Objects.requireNonNull(category, "category must not be null");
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be zero or greater");
    }
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    path = path == null ? List.of() : List.copyOf(path);
    if (selectable && disabledReason != null) {
      throw new IllegalArgumentException("selectable options must not have a disabled reason");
    }
    if (!selectable && disabledReason == null) {
      throw new IllegalArgumentException("disabled options must have a disabled reason");
    }
  }

  public static CatalogueCategoryParentOption from(
    CatalogueCategory category,
    int depth,
    List<CatalogueCategoryBreadcrumbItem> path
  ) {
    boolean selectable = category.active();
    return new CatalogueCategoryParentOption(
      category,
      depth,
      category.name(),
      path,
      selectable,
      selectable ? null : CatalogueCategoryParentOptionDisabledReason.INACTIVE
    );
  }

  public CatalogueCategoryParentOption withLabel(String newLabel) {
    return new CatalogueCategoryParentOption(
      category,
      depth,
      newLabel,
      path,
      selectable,
      disabledReason
    );
  }
}
