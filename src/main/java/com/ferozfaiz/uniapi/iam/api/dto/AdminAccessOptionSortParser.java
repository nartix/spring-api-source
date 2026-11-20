package com.ferozfaiz.uniapi.iam.api.dto;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.ArrayList;
import java.util.List;

final class AdminAccessOptionSortParser {

  private static final int MAX_SORT_ORDERS = 5;

  private AdminAccessOptionSortParser() {}

  static SortCriteria parse(
    List<String> sort,
    String direction,
    List<String> allowedSorts,
    List<SortOrder> defaultOrders
  ) {
    List<String> sortValues =
      sort == null
        ? List.of()
        : sort
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList();

    if (sortValues.isEmpty()) {
      return new SortCriteria(withTieBreaker(defaultOrders));
    }

    List<SortOrder> orders = parseSortOrders(sortValues, direction, allowedSorts);
    if (orders.size() > MAX_SORT_ORDERS) {
      throw invalidRequest(
        "iam.api.admin-access-options.search.sort.max",
        "sort may contain at most " + MAX_SORT_ORDERS + " orders",
        MAX_SORT_ORDERS
      );
    }
    return new SortCriteria(withTieBreaker(orders));
  }

  private static List<SortOrder> parseSortOrders(
    List<String> sortValues,
    String direction,
    List<String> allowedSorts
  ) {
    if (sortValues.stream().anyMatch(value -> value.contains(","))) {
      return sortValues
        .stream()
        .map(value -> parseDelimitedSortOrder(value, allowedSorts))
        .toList();
    }

    if (sortValues.size() == 1) {
      return List.of(
        toSortOrder(sortValues.getFirst(), fallbackDirection(direction), allowedSorts)
      );
    }

    if (sortValues.size() % 2 == 0 && everySecondValueIsDirection(sortValues)) {
      List<SortOrder> orders = new ArrayList<>();
      for (int index = 0; index < sortValues.size(); index += 2) {
        orders.add(
          toSortOrder(
            sortValues.get(index),
            parseDirection(sortValues.get(index + 1)),
            allowedSorts
          )
        );
      }
      return orders;
    }

    SortDirection fallbackDirection = fallbackDirection(direction);
    return sortValues
      .stream()
      .map(property -> toSortOrder(property, fallbackDirection, allowedSorts))
      .toList();
  }

  private static SortOrder parseDelimitedSortOrder(String value, List<String> allowedSorts) {
    String[] parts = value.split(",");
    if (parts.length != 2) {
      throw invalidRequest(
        "iam.api.admin-access-options.search.sort.format",
        "sort must use field,direction format"
      );
    }
    return toSortOrder(parts[0], parseDirection(parts[1]), allowedSorts);
  }

  private static SortOrder toSortOrder(
    String property,
    SortDirection sortDirection,
    List<String> allowedSorts
  ) {
    String trimmedProperty = property.trim();
    if (!allowedSorts.contains(trimmedProperty)) {
      String allowed = String.join(", ", allowedSorts);
      throw invalidRequest(
        "iam.api.admin-access-options.search.sort.allowed",
        "sort must use one of: " + allowed,
        allowed
      );
    }
    return new SortOrder(trimmedProperty, sortDirection);
  }

  private static List<SortOrder> withTieBreaker(List<SortOrder> orders) {
    if (orders.stream().anyMatch(order -> "id".equals(order.property()))) {
      return orders;
    }
    List<SortOrder> stableOrders = new ArrayList<>(orders);
    stableOrders.add(new SortOrder("id", SortDirection.ASC));
    return stableOrders;
  }

  private static SortDirection fallbackDirection(String direction) {
    return direction == null || direction.isBlank() ? SortDirection.ASC : parseDirection(direction);
  }

  private static boolean everySecondValueIsDirection(List<String> sortValues) {
    for (int index = 1; index < sortValues.size(); index += 2) {
      if (!isDirection(sortValues.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static SortDirection parseDirection(String value) {
    if (!isDirection(value)) {
      throw invalidRequest(
        "iam.api.admin-access-options.search.direction.pattern",
        "direction must be asc or desc"
      );
    }
    return SortDirection.from(value);
  }

  private static boolean isDirection(String value) {
    return (
      value != null &&
      ("asc".equalsIgnoreCase(value.trim()) || "desc".equalsIgnoreCase(value.trim()))
    );
  }

  private static InvalidRequestException invalidRequest(
    String messageKey,
    String defaultMessage,
    Object... args
  ) {
    return new InvalidRequestException(messageKey, defaultMessage, args);
  }
}
