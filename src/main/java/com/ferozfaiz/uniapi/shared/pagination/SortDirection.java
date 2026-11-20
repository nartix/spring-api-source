package com.ferozfaiz.uniapi.shared.pagination;

import java.util.Locale;

public enum SortDirection {
  ASC,
  DESC;

  public static SortDirection from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("sort direction is required");
    }
    return SortDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
