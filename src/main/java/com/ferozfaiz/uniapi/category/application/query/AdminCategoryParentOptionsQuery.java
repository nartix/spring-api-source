package com.ferozfaiz.uniapi.category.application.query;

import java.util.List;

public record AdminCategoryParentOptionsQuery(
  String q,
  int limit,
  String cursor,
  List<Long> includeIds,
  Long excludeSubtreeId,
  boolean includeInactive
) {
  public AdminCategoryParentOptionsQuery {
    includeIds = includeIds == null ? List.of() : List.copyOf(includeIds);
  }
}
