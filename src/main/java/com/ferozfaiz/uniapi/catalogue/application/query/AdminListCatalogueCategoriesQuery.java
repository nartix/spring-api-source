package com.ferozfaiz.uniapi.catalogue.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record AdminListCatalogueCategoriesQuery(
  CategoryParentScope parentScope,
  AdminCatalogueCategoryFilters filters,
  PageCriteria page,
  SortCriteria sort
) {
  public AdminListCatalogueCategoriesQuery {
    Objects.requireNonNull(parentScope, "parentScope must not be null");
    Objects.requireNonNull(filters, "filters must not be null");
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
