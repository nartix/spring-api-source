package com.ferozfaiz.uniapi.category.api.dto;

import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryTreePolicy;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Bounded full-tree request options for generic admin categories")
public record AdminCategoryTreeRequest(
  Integer page,
  Integer size,
  @Schema(description = "Sibling sort order for every tree level. Use field,direction.")
  List<String> sort,
  String direction
) {
  private static final String DEFAULT_SORT = "sortRank";
  private static final SortDirection DEFAULT_DIRECTION = SortDirection.ASC;
  private static final int MAX_PRIMARY_SORT_ORDERS = 1;
  private static final List<String> ALLOWED_SORTS = List.of(
    "id",
    "slug",
    "name",
    "sortRank",
    "active",
    "createdAt",
    "updatedAt"
  );

  public AdminCategoryTreeQuery toQuery(
    CategorySystemDescriptor descriptor,
    AdminCategoryTreePolicy policy
  ) {
    return new AdminCategoryTreeQuery(policy.resolvePage(page, size), toSortCriteria(descriptor));
  }

  private SortCriteria toSortCriteria(CategorySystemDescriptor descriptor) {
    List<String> sortValues =
      sort == null
        ? List.of()
        : sort
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList();

    boolean sortRankAvailable = exposesSortRank(descriptor);
    List<SortOrder> fallbackSortOrders = fallbackSortOrders(descriptor);
    if (sortValues.isEmpty()) {
      return new SortCriteria(withTieBreaker(defaultSort(sortRankAvailable, fallbackSortOrders)));
    }

    List<SortOrder> orders = parseSortOrders(sortValues, sortRankAvailable);
    if (orders.size() > MAX_PRIMARY_SORT_ORDERS) {
      throw invalidRequest(
        "category.api.list.sort.max",
        "complete tree requests support at most one primary sort order",
        MAX_PRIMARY_SORT_ORDERS
      );
    }
    return new SortCriteria(withTieBreaker(orders));
  }

  private List<SortOrder> parseSortOrders(List<String> sortValues, boolean sortRankAvailable) {
    if (sortValues.stream().anyMatch(value -> value.contains(","))) {
      return sortValues
        .stream()
        .map(value -> parseDelimitedSortOrder(value, sortRankAvailable))
        .toList();
    }

    if (sortValues.size() == 1) {
      return List.of(toSortOrder(sortValues.getFirst(), fallbackDirection(), sortRankAvailable));
    }

    if (sortValues.size() % 2 == 0 && everySecondValueIsDirection(sortValues)) {
      List<SortOrder> orders = new ArrayList<>();
      for (int index = 0; index < sortValues.size(); index += 2) {
        orders.add(
          toSortOrder(
            sortValues.get(index),
            parseDirection(sortValues.get(index + 1)),
            sortRankAvailable
          )
        );
      }
      return orders;
    }

    if (sortValues.stream().anyMatch(AdminCategoryTreeRequest::isDirection)) {
      throw invalidRequest("category.api.list.sort.format", "sort must use field,direction format");
    }

    SortDirection fallbackDirection = fallbackDirection();
    return sortValues
      .stream()
      .map(property -> toSortOrder(property, fallbackDirection, sortRankAvailable))
      .toList();
  }

  private SortOrder parseDelimitedSortOrder(String value, boolean sortRankAvailable) {
    String[] parts = value.split(",");
    if (parts.length != 2) {
      throw invalidRequest("category.api.list.sort.format", "sort must use field,direction format");
    }
    return toSortOrder(parts[0], parseDirection(parts[1]), sortRankAvailable);
  }

  private SortOrder toSortOrder(
    String property,
    SortDirection sortDirection,
    boolean sortRankAvailable
  ) {
    String trimmedProperty = property.trim();
    if (!ALLOWED_SORTS.contains(trimmedProperty)) {
      String allowedSorts = String.join(", ", ALLOWED_SORTS);
      throw invalidRequest(
        "category.api.list.sort.allowed",
        "sort must use one of: " + allowedSorts,
        allowedSorts
      );
    }
    if (
      "sortRank".equals(trimmedProperty) &&
      (!sortRankAvailable || sortDirection != SortDirection.ASC)
    ) {
      throw invalidRequest(
        "category.api.list.sort.sort-rank.unsupported",
        "sortRank sorting is only supported for ascending ranked root, tree, or sibling-scoped category views"
      );
    }
    return new SortOrder(trimmedProperty, sortDirection);
  }

  private static List<SortOrder> defaultSort(
    boolean sortRankAvailable,
    List<SortOrder> fallbackSortOrders
  ) {
    return sortRankAvailable
      ? List.of(new SortOrder(DEFAULT_SORT, DEFAULT_DIRECTION))
      : fallbackSortOrders;
  }

  private static boolean exposesSortRank(CategorySystemDescriptor descriptor) {
    return descriptor.ranking().rankingEnabled() && descriptor.ranking().sortRankExposed();
  }

  private static List<SortOrder> fallbackSortOrders(CategorySystemDescriptor descriptor) {
    return descriptor
      .fallbackOrder()
      .orders()
      .stream()
      .map(AdminCategoryTreeRequest::toSortOrder)
      .toList();
  }

  private static SortOrder toSortOrder(FallbackOrderDescriptor.Order order) {
    if (!ALLOWED_SORTS.contains(order.property())) {
      throw new IllegalStateException(
        "Unsupported category tree fallback sort property: " + order.property()
      );
    }
    return new SortOrder(order.property(), toSortDirection(order.direction()));
  }

  private static SortDirection toSortDirection(FallbackOrderDescriptor.Direction direction) {
    return direction == FallbackOrderDescriptor.Direction.ASC
      ? SortDirection.ASC
      : SortDirection.DESC;
  }

  private static List<SortOrder> withTieBreaker(List<SortOrder> orders) {
    if (orders.stream().anyMatch(order -> "id".equals(order.property()))) {
      return orders;
    }
    List<SortOrder> stableOrders = new ArrayList<>(orders);
    stableOrders.add(new SortOrder("id", SortDirection.ASC));
    return List.copyOf(stableOrders);
  }

  private SortDirection fallbackDirection() {
    return direction == null || direction.isBlank() ? DEFAULT_DIRECTION : parseDirection(direction);
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
        "category.api.list.sort.direction",
        "sort direction must be asc or desc"
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
