package com.ferozfaiz.uniapi.iam.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record AdminListRolesQuery(PageCriteria page, SortCriteria sort, String q, Boolean active) {
  public AdminListRolesQuery {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
