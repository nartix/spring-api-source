package com.ferozfaiz.uniapi.shared.pagination;

import java.util.List;

public record SortCriteria(List<SortOrder> orders) {
  public SortCriteria {
    if (orders == null || orders.isEmpty()) {
      throw new IllegalArgumentException("sort orders must not be empty");
    }
    orders = List.copyOf(orders);
    orders.forEach(order -> {
      if (order == null) {
        throw new IllegalArgumentException("sort order must not be null");
      }
    });
  }

  public static SortCriteria of(String property, SortDirection direction) {
    return new SortCriteria(List.of(new SortOrder(property, direction)));
  }
}
