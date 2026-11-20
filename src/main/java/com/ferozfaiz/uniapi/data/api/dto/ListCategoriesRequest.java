package com.ferozfaiz.uniapi.data.api.dto;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Pagination and sorting options for listing categories")
public record ListCategoriesRequest(
  @Min(value = 0, message = "{data.api.category.list.page.min}")
  @Schema(description = "Zero-based page index", example = "0", defaultValue = "0", minimum = "0")
  Integer page,
  @Min(value = 1, message = "{data.api.category.list.size.min}")
  @Schema(
    description = "Number of categories per page. The maximum is controlled by application category list policy.",
    example = "20",
    defaultValue = "20",
    minimum = "1"
  )
  Integer size,
  @Schema(
    description = "Sort orders. Repeat the parameter for multiple fields, using field,direction.",
    example = "[\"tree,asc\", \"name,asc\"]",
    defaultValue = "[\"depth,asc\", \"name,asc\", \"id,asc\"]"
  )
  List<String> sort,
  @Pattern(regexp = "(?i)asc|desc", message = "{data.api.category.list.direction.pattern}")
  @Schema(
    description = "Fallback sort direction for a single sort field without an inline direction",
    example = "asc",
    defaultValue = "asc",
    allowableValues = { "asc", "desc" }
  )
  String direction
) {
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final SortDirection DEFAULT_DIRECTION = SortDirection.ASC;
  private static final int MAX_SORT_ORDERS = 5;
  private static final String TREE_SORT = "tree";
  private static final List<String> DEFAULT_SORTS = List.of("depth", "name", "id");
  private static final List<String> ALLOWED_SORTS = List.of(
    "id",
    "parentId",
    "depth",
    "tree",
    "sortRank",
    "slug",
    "name",
    "active",
    "createdAt",
    "updatedAt"
  );
  private static final List<String> TREE_SECONDARY_SORTS = List.of(
    "id",
    "sortRank",
    "slug",
    "name",
    "active",
    "createdAt",
    "updatedAt"
  );

  public PageCriteria toPageCriteria() {
    return new PageCriteria(page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
  }

  public SortCriteria toSortCriteria() {
    List<String> sortValues =
      sort == null
        ? List.of()
        : sort
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList();

    if (sortValues.isEmpty()) {
      return new SortCriteria(
        DEFAULT_SORTS.stream()
          .map(property -> new SortOrder(property, DEFAULT_DIRECTION))
          .toList()
      );
    }

    List<SortOrder> orders = validateTreeSort(parseSortOrders(sortValues));
    if (orders.size() > MAX_SORT_ORDERS) {
      throw invalidRequest(
        "data.api.category.list.sort.max",
        "sort may contain at most " + MAX_SORT_ORDERS + " orders",
        MAX_SORT_ORDERS
      );
    }
    return new SortCriteria(orders);
  }

  private List<SortOrder> validateTreeSort(List<SortOrder> orders) {
    List<SortOrder> treeOrders = orders
      .stream()
      .filter(order -> TREE_SORT.equals(order.property()))
      .toList();
    if (treeOrders.isEmpty()) {
      return orders;
    }
    if (treeOrders.size() > 1) {
      throw invalidRequest(
        "data.api.category.list.sort.tree.duplicate",
        "tree sort may be used only once"
      );
    }
    if (!TREE_SORT.equals(orders.getFirst().property())) {
      throw invalidRequest(
        "data.api.category.list.sort.tree.position",
        "tree sort must be the first sort order"
      );
    }
    if (orders.getFirst().direction() != SortDirection.ASC) {
      throw invalidRequest(
        "data.api.category.list.sort.tree.direction",
        "tree sort only supports asc direction"
      );
    }
    List<String> invalidSecondarySorts = orders
      .stream()
      .skip(1)
      .map(SortOrder::property)
      .filter(property -> !TREE_SECONDARY_SORTS.contains(property))
      .toList();
    if (!invalidSecondarySorts.isEmpty()) {
      String allowedSorts = String.join(", ", TREE_SECONDARY_SORTS);
      throw invalidRequest(
        "data.api.category.list.sort.tree.secondary",
        "tree sort secondary orders must use one of: " + allowedSorts,
        allowedSorts
      );
    }
    return orders;
  }

  private List<SortOrder> parseSortOrders(List<String> sortValues) {
    if (sortValues.stream().anyMatch(value -> value.contains(","))) {
      return sortValues.stream().map(this::parseDelimitedSortOrder).toList();
    }

    if (sortValues.size() == 1) {
      return List.of(toSortOrder(sortValues.getFirst(), fallbackDirection()));
    }

    if (sortValues.size() % 2 == 0 && everySecondValueIsDirection(sortValues)) {
      List<SortOrder> orders = new ArrayList<>();
      for (int index = 0; index < sortValues.size(); index += 2) {
        orders.add(toSortOrder(sortValues.get(index), parseDirection(sortValues.get(index + 1))));
      }
      return orders;
    }

    SortDirection fallbackDirection = fallbackDirection();
    return sortValues
      .stream()
      .map(property -> toSortOrder(property, fallbackDirection))
      .toList();
  }

  private SortOrder parseDelimitedSortOrder(String value) {
    String[] parts = value.split(",");
    if (parts.length != 2) {
      throw invalidRequest(
        "data.api.category.list.sort.format",
        "sort must use field,direction format"
      );
    }
    return toSortOrder(parts[0], parseDirection(parts[1]));
  }

  private SortOrder toSortOrder(String property, SortDirection sortDirection) {
    String trimmedProperty = property.trim();
    if (!ALLOWED_SORTS.contains(trimmedProperty)) {
      String allowedSorts = String.join(", ", ALLOWED_SORTS);
      throw invalidRequest(
        "data.api.category.list.sort.allowed",
        "sort must use one of: " + allowedSorts,
        allowedSorts
      );
    }
    return new SortOrder(trimmedProperty, sortDirection);
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
        "data.api.category.list.direction.pattern",
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
