package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.List;
import java.util.Locale;

public record FallbackOrderDescriptor(List<Order> orders) {
  public FallbackOrderDescriptor {
    orders = List.copyOf(orders);
    if (orders.isEmpty()) {
      throw new IllegalArgumentException("fallbackOrder must contain at least one order");
    }
    boolean hasDeterministicIdTieBreaker = orders
      .stream()
      .anyMatch(order -> "id".equals(order.property()) && order.direction() == Direction.ASC);
    if (!hasDeterministicIdTieBreaker) {
      throw new IllegalArgumentException("fallbackOrder must include id ASC as a tie-breaker");
    }
  }

  public enum Direction {
    ASC,
    DESC;

    public static Direction from(String value) {
      return Direction.valueOf(
        SqlIdentifiers.requireText(value, "direction").toUpperCase(Locale.ROOT)
      );
    }
  }

  public record Order(String property, String columnName, Direction direction) {
    public Order {
      property = SqlIdentifiers.requireText(property, "property");
      columnName = SqlIdentifiers.requireIdentifier(columnName, "columnName");
      java.util.Objects.requireNonNull(direction, "direction");
    }
  }
}
