package com.ferozfaiz.uniapi.shared.pagination;

public record PageCriteria(int page, int size) {
  public PageCriteria {
    if (page < 0) {
      throw new IllegalArgumentException("page must be zero or greater");
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1");
    }
  }
}
