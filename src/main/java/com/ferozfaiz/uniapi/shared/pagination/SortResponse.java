package com.ferozfaiz.uniapi.shared.pagination;

import java.util.List;

public record SortResponse(String property, SortDirection direction) {
  public static SortResponse from(SortCriteria sort) {
    return from(sort.orders().getFirst());
  }

  public static SortResponse from(SortOrder sort) {
    return new SortResponse(sort.property(), sort.direction());
  }

  public static List<SortResponse> fromAll(SortCriteria sort) {
    return sort.orders().stream().map(SortResponse::from).toList();
  }
}
