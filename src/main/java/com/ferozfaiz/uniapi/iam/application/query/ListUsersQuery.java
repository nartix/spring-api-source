package com.ferozfaiz.uniapi.iam.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record ListUsersQuery(PageCriteria page, SortCriteria sort) {
  public ListUsersQuery {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
