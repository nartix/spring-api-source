package com.ferozfaiz.uniapi.catalogue.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record AdminCatalogueCategoryTreeQuery(PageCriteria page, SortCriteria sort) {
  public AdminCatalogueCategoryTreeQuery {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
