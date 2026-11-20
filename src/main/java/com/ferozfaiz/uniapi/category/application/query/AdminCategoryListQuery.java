package com.ferozfaiz.uniapi.category.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record AdminCategoryListQuery(
  AdminCategoryParentScope parentScope,
  AdminCategoryFilters filters,
  PageCriteria page,
  SortCriteria sort
) {
  public AdminCategoryListQuery {
    Objects.requireNonNull(parentScope, "parentScope must not be null");
    Objects.requireNonNull(filters, "filters must not be null");
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
