package com.ferozfaiz.uniapi.category.api.dto;

import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Pagination, filtering, and sorting options for generic admin categories")
public record AdminCategoryListRequest(
  Integer page,
  Integer size,
  @Schema(
    description = "Optional parent scope. Omitted lists all categories, 0 lists roots, and a positive value lists direct children."
  )
  String parentId,
  @Schema(description = "Optional active-state filter") Boolean active,
  @Schema(description = "Trimmed search over category name and slug", maxLength = 120) String q,
  @Schema(description = "Trimmed exact slug filter") String slug,
  @Schema(description = "Repeated sort orders using field,direction") List<String> sort,
  String direction
) {
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 1000;
  private static final int MAX_Q_LENGTH = 120;
  private static final String ALL_DEFAULT_SORT = "updatedAt";
  private static final SortDirection ALL_DEFAULT_DIRECTION = SortDirection.DESC;
  private static final String SIBLING_DEFAULT_SORT = "sortRank";
  private static final SortDirection SIBLING_DEFAULT_DIRECTION = SortDirection.ASC;
  private static final int MAX_SORT_ORDERS = 5;
  private static final java.util.regex.Pattern POSITIVE_OR_ZERO_INTEGER =
    java.util.regex.Pattern.compile("^[0-9]+$");
  private static final java.util.regex.Pattern SLUG_PATTERN = java.util.regex.Pattern.compile(
    "^[a-z0-9]+(-[a-z0-9]+)*$"
  );
  private static final List<String> ALLOWED_SORTS = List.of(
    "id",
    "parentId",
    "slug",
    "name",
    "sortRank",
    "active",
    "createdAt",
    "updatedAt"
  );

  public AdminCategoryListQuery toQuery(CategorySystemDescriptor descriptor) {
    AdminCategoryParentScope parentScope = toParentScope();
    return new AdminCategoryListQuery(
      parentScope,
      toFilters(),
      toPageCriteria(),
      toSortCriteria(parentScope, descriptor)
    );
  }

  public AdminCategoryParentScope toParentScope() {
    if (parentId == null) {
      return AdminCategoryParentScope.all();
    }
    String value = parentId.trim();
    if (value.isEmpty() || !POSITIVE_OR_ZERO_INTEGER.matcher(value).matches()) {
      throw invalidRequest(
        "category.api.list.parent-id.format",
        "parentId must be omitted, 0, or a positive integer"
      );
    }
    long resolvedParentId;
    try {
      resolvedParentId = Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw invalidRequest(
        "category.api.list.parent-id.format",
        "parentId must be omitted, 0, or a positive integer"
      );
    }
    if (resolvedParentId == 0) {
      return AdminCategoryParentScope.roots();
    }
    return AdminCategoryParentScope.children(resolvedParentId);
  }

  public AdminCategoryFilters toFilters() {
    String normalizedQ = blankToNull(q);
    if (normalizedQ != null && normalizedQ.length() > MAX_Q_LENGTH) {
      throw invalidRequest(
        "category.api.list.search.size",
        "search must be at most " + MAX_Q_LENGTH + " characters",
        MAX_Q_LENGTH
      );
    }
    String normalizedSlug = blankToNull(slug);
    if (normalizedSlug != null && !SLUG_PATTERN.matcher(normalizedSlug).matches()) {
      throw invalidRequest(
        "category.api.list.slug.format",
        "slug may contain lowercase letters, numbers, and hyphens"
      );
    }
    return new AdminCategoryFilters(active, normalizedQ, normalizedSlug);
  }

  public PageCriteria toPageCriteria() {
    int resolvedSize = size == null ? DEFAULT_SIZE : size;
    if (resolvedSize > MAX_SIZE) {
      throw invalidRequest(
        "category.api.list.size.max",
        "size must be at most " + MAX_SIZE,
        MAX_SIZE
      );
    }
    return new PageCriteria(page == null ? DEFAULT_PAGE : page, resolvedSize);
  }

  private SortCriteria toSortCriteria(
    AdminCategoryParentScope parentScope,
    CategorySystemDescriptor descriptor
  ) {
    return toSortCriteria(parentScope, exposesSortRank(descriptor), fallbackSortOrders(descriptor));
  }

  private SortCriteria toSortCriteria(
    AdminCategoryParentScope parentScope,
    boolean sortRankAvailable,
    List<SortOrder> fallbackSortOrders
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
      return new SortCriteria(
        withTieBreaker(defaultSort(parentScope, sortRankAvailable, fallbackSortOrders))
      );
    }

    List<SortOrder> orders = parseSortOrders(sortValues, parentScope, sortRankAvailable);
    if (orders.size() > MAX_SORT_ORDERS) {
      throw invalidRequest(
        "category.api.list.sort.max",
        "sort may contain at most " + MAX_SORT_ORDERS + " orders",
        MAX_SORT_ORDERS
      );
    }
    rejectDuplicateProperties(orders);
    return new SortCriteria(withTieBreaker(orders));
  }

  private List<SortOrder> parseSortOrders(
    List<String> sortValues,
    AdminCategoryParentScope parentScope,
    boolean sortRankAvailable
  ) {
    if (sortValues.stream().anyMatch(value -> value.contains(","))) {
      return sortValues
        .stream()
        .map(value -> parseDelimitedSortOrder(value, parentScope, sortRankAvailable))
        .toList();
    }

    if (sortValues.size() == 1) {
      return List.of(
        toSortOrder(sortValues.getFirst(), fallbackDirection(), parentScope, sortRankAvailable)
      );
    }

    if (sortValues.size() % 2 == 0 && everySecondValueIsDirection(sortValues)) {
      List<SortOrder> orders = new ArrayList<>();
      for (int index = 0; index < sortValues.size(); index += 2) {
        orders.add(
          toSortOrder(
            sortValues.get(index),
            parseDirection(sortValues.get(index + 1)),
            parentScope,
            sortRankAvailable
          )
        );
      }
      return orders;
    }

    if (sortValues.stream().anyMatch(AdminCategoryListRequest::isDirection)) {
      throw invalidRequest("category.api.list.sort.format", "sort must use field,direction format");
    }

    SortDirection fallbackDirection = fallbackDirection();
    return sortValues
      .stream()
      .map(property -> toSortOrder(property, fallbackDirection, parentScope, sortRankAvailable))
      .toList();
  }

  private SortOrder parseDelimitedSortOrder(
    String value,
    AdminCategoryParentScope parentScope,
    boolean sortRankAvailable
  ) {
    String[] parts = value.split(",");
    if (parts.length != 2) {
      throw invalidRequest("category.api.list.sort.format", "sort must use field,direction format");
    }
    return toSortOrder(parts[0], parseDirection(parts[1]), parentScope, sortRankAvailable);
  }

  private SortOrder toSortOrder(
    String property,
    SortDirection sortDirection,
    AdminCategoryParentScope parentScope,
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
      (!sortRankAvailable || sortDirection != SortDirection.ASC || !parentScope.isSiblingScoped())
    ) {
      throw invalidRequest(
        "category.api.list.sort.sort-rank.unsupported",
        "sortRank sorting is only supported for ascending ranked root, tree, or sibling-scoped category views"
      );
    }
    return new SortOrder(trimmedProperty, sortDirection);
  }

  private static List<SortOrder> defaultSort(
    AdminCategoryParentScope parentScope,
    boolean sortRankAvailable,
    List<SortOrder> fallbackSortOrders
  ) {
    if (parentScope.isSiblingScoped()) {
      return sortRankAvailable
        ? List.of(new SortOrder(SIBLING_DEFAULT_SORT, SIBLING_DEFAULT_DIRECTION))
        : fallbackSortOrders;
    }
    return List.of(new SortOrder(ALL_DEFAULT_SORT, ALL_DEFAULT_DIRECTION));
  }

  private static boolean exposesSortRank(CategorySystemDescriptor descriptor) {
    return descriptor.ranking().rankingEnabled() && descriptor.ranking().sortRankExposed();
  }

  private static List<SortOrder> fallbackSortOrders(CategorySystemDescriptor descriptor) {
    return descriptor
      .fallbackOrder()
      .orders()
      .stream()
      .map(AdminCategoryListRequest::toSortOrder)
      .toList();
  }

  private static SortOrder toSortOrder(FallbackOrderDescriptor.Order order) {
    if (!ALLOWED_SORTS.contains(order.property())) {
      throw new IllegalStateException(
        "Unsupported category fallback sort property: " + order.property()
      );
    }
    return new SortOrder(order.property(), toSortDirection(order.direction()));
  }

  private static SortDirection toSortDirection(FallbackOrderDescriptor.Direction direction) {
    return direction == FallbackOrderDescriptor.Direction.ASC
      ? SortDirection.ASC
      : SortDirection.DESC;
  }

  private static void rejectDuplicateProperties(List<SortOrder> orders) {
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (SortOrder order : orders) {
      if (!seen.add(order.property())) {
        throw invalidRequest(
          "category.api.list.sort.duplicate",
          "sort must not repeat the same property"
        );
      }
    }
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
    return direction == null || direction.isBlank()
      ? SortDirection.DESC
      : parseDirection(direction);
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

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static InvalidRequestException invalidRequest(
    String messageKey,
    String defaultMessage,
    Object... args
  ) {
    return new InvalidRequestException(messageKey, defaultMessage, args);
  }
}
