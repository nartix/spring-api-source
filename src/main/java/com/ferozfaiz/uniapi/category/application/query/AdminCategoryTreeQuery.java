package com.ferozfaiz.uniapi.category.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record AdminCategoryTreeQuery(PageCriteria page, SortCriteria sort) {
  public AdminCategoryTreeQuery {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
