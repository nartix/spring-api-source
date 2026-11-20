package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.util.List;

public record CatalogueCategoryParentOptionSlice(
  List<CatalogueCategoryParentOption> data,
  boolean hasMore,
  String nextCursor
) {
  public CatalogueCategoryParentOptionSlice {
    data = data == null ? List.of() : List.copyOf(data);
    if (!hasMore && nextCursor != null) {
      throw new IllegalArgumentException("nextCursor must be null when hasMore is false");
    }
    if (hasMore && (nextCursor == null || nextCursor.isBlank())) {
      throw new IllegalArgumentException("nextCursor is required when hasMore is true");
    }
  }
}
