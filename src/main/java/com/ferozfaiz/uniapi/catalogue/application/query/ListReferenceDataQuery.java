package com.ferozfaiz.uniapi.catalogue.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;

public record ListReferenceDataQuery(
  PageCriteria page,
  SortCriteria sort,
  String search,
  Boolean active
) {}
