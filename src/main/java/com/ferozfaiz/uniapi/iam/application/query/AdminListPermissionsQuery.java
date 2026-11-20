package com.ferozfaiz.uniapi.iam.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Objects;

public record AdminListPermissionsQuery(
  PageCriteria page,
  SortCriteria sort,
  String q,
  String resource,
  String action,
  Boolean active
) {
  public AdminListPermissionsQuery {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
  }
}
