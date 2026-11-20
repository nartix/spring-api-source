package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsCursor;
import java.util.LinkedHashSet;
import java.util.List;

public record CatalogueCategoryParentOptionLookup(
  String q,
  Long numericQuery,
  int limit,
  AdminCatalogueCategoryParentOptionsCursor cursor,
  Long excludeSubtreeId,
  List<Long> includeIds,
  boolean includeInactive
) {
  public CatalogueCategoryParentOptionLookup {
    includeIds = includeIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(includeIds));
  }

  public AdminCatalogueCategoryParentOptionsCursor.Mode mode() {
    return q == null
      ? AdminCatalogueCategoryParentOptionsCursor.Mode.DEFAULT
      : AdminCatalogueCategoryParentOptionsCursor.Mode.SEARCH;
  }
}
