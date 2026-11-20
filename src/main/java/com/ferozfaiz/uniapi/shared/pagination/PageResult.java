package com.ferozfaiz.uniapi.shared.pagination;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
  List<T> content,
  int page,
  int size,
  long totalElements,
  int totalPages,
  boolean first,
  boolean last,
  SortCriteria sort
) {
  public PageResult {
    content = List.copyOf(content);
    if (page < 0) {
      throw new IllegalArgumentException("page must be zero or greater");
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1");
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("totalElements must be zero or greater");
    }
    if (totalPages < 0) {
      throw new IllegalArgumentException("totalPages must be zero or greater");
    }
  }

  public <R> PageResult<R> map(Function<T, R> mapper) {
    return new PageResult<>(
      content.stream().map(mapper).toList(),
      page,
      size,
      totalElements,
      totalPages,
      first,
      last,
      sort
    );
  }
}
