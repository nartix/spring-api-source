package com.ferozfaiz.uniapi.iam.api.dto;

import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(description = "Filters, pagination, and sorting options for listing admin users")
public record AdminListUsersRequest(
  @Min(value = 0, message = "{iam.api.user.search.page.min}")
  @Schema(description = "Zero-based page index", example = "0", defaultValue = "0", minimum = "0")
  Integer page,
  @Min(value = 1, message = "{iam.api.user.search.size.min}")
  @Max(value = 100, message = "{iam.api.user.search.size.max}")
  @Schema(
    description = "Number of users per page",
    example = "20",
    defaultValue = "20",
    minimum = "1",
    maximum = "100"
  )
  Integer size,
  @Schema(
    description = "Sort orders. Repeat the parameter for multiple fields, using field,direction.",
    example = "[\"updatedAt,desc\", \"username,asc\"]",
    defaultValue = "updatedAt,desc"
  )
  List<String> sort,
  @Size(max = 120, message = "{iam.api.user.search.query.size}")
  @Schema(description = "Global search across admin user list fields", example = "feroz admin")
  String search,
  @Schema(description = "Filter by user id", example = "1") Long id,
  @Size(max = 255, message = "{iam.api.user.search.filter.size}")
  @Schema(description = "Filter by username", example = "feroz")
  String username,
  @Size(max = 255, message = "{iam.api.user.search.filter.size}")
  @Schema(description = "Filter by email", example = "feroz@example.com")
  String email,
  @Size(max = 255, message = "{iam.api.user.search.filter.size}")
  @Schema(description = "Filter by first name", example = "Feroz")
  String firstName,
  @Size(max = 255, message = "{iam.api.user.search.filter.size}")
  @Schema(description = "Filter by last name", example = "Faiz")
  String lastName,
  @Schema(description = "Filter by active status", example = "true") Boolean active,
  @Schema(description = "Filter by account non-expired status", example = "true")
  Boolean accountNonExpired,
  @Schema(description = "Filter by account non-locked status", example = "true")
  Boolean accountNonLocked,
  @Schema(description = "Filter by credentials non-expired status", example = "true")
  Boolean credentialsNonExpired,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Inclusive lower bound for last login timestamp")
  OffsetDateTime lastLoginFrom,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Exclusive upper bound for last login timestamp")
  OffsetDateTime lastLoginTo,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Inclusive lower bound for last activity timestamp")
  OffsetDateTime lastActivityFrom,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Exclusive upper bound for last activity timestamp")
  OffsetDateTime lastActivityTo,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Inclusive lower bound for creation timestamp")
  OffsetDateTime createdFrom,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Exclusive upper bound for creation timestamp")
  OffsetDateTime createdTo,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Inclusive lower bound for update timestamp")
  OffsetDateTime updatedFrom,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Schema(description = "Exclusive upper bound for update timestamp")
  OffsetDateTime updatedTo,
  @Size(max = 255, message = "{iam.api.user.search.filter.size}")
  @Schema(description = "Filter by creator", example = "system")
  String createdBy,
  @Size(max = 255, message = "{iam.api.user.search.filter.size}")
  @Schema(description = "Filter by last updater", example = "system")
  String updatedBy,
  @Size(max = 50, message = "{iam.api.user.search.role.size}")
  @Schema(description = "Filter by role codes or names. Repeat the parameter for multiple values.")
  List<String> roles,
  @Size(max = 50, message = "{iam.api.user.search.role.size}")
  @Schema(description = "Alias for roles. Repeat the parameter for multiple values.")
  List<String> role,
  @Size(max = 50, message = "{iam.api.user.search.permission.size}")
  @Schema(
    description = "Filter by permission codes or names. Repeat the parameter for multiple values."
  )
  List<String> permissions,
  @Size(max = 50, message = "{iam.api.user.search.permission.size}")
  @Schema(description = "Alias for permissions. Repeat the parameter for multiple values.")
  List<String> permission,
  @Pattern(regexp = "(?i)asc|desc", message = "{iam.api.user.search.direction.pattern}")
  @Schema(
    description = "Fallback sort direction for a single sort field without an inline direction",
    example = "desc",
    defaultValue = "desc",
    allowableValues = { "asc", "desc" }
  )
  String direction
) {
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final String DEFAULT_SORT = "updatedAt";
  private static final SortDirection DEFAULT_DIRECTION = SortDirection.DESC;
  private static final int MAX_SORT_ORDERS = 5;
  private static final List<String> ALLOWED_SORTS = List.of(
    "id",
    "username",
    "email",
    "firstName",
    "lastName",
    "active",
    "accountNonExpired",
    "accountNonLocked",
    "credentialsNonExpired",
    "lastLoginAt",
    "lastActivityAt",
    "createdAt",
    "updatedAt",
    "createdBy",
    "updatedBy",
    "roles",
    "permissions"
  );

  public AdminListUsersQuery toQuery() {
    validateRange("lastLogin", lastLoginFrom, lastLoginTo);
    validateRange("lastActivity", lastActivityFrom, lastActivityTo);
    validateRange("created", createdFrom, createdTo);
    validateRange("updated", updatedFrom, updatedTo);

    return new AdminListUsersQuery(
      toPageCriteria(),
      toSortCriteria(),
      blankToNull(search),
      id,
      blankToNull(username),
      blankToNull(email),
      blankToNull(firstName),
      blankToNull(lastName),
      active,
      accountNonExpired,
      accountNonLocked,
      credentialsNonExpired,
      lastLoginFrom,
      lastLoginTo,
      lastActivityFrom,
      lastActivityTo,
      createdFrom,
      createdTo,
      updatedFrom,
      updatedTo,
      blankToNull(createdBy),
      blankToNull(updatedBy),
      normalizedValues(roles, role),
      normalizedValues(permissions, permission)
    );
  }

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
        withTieBreaker(List.of(new SortOrder(DEFAULT_SORT, DEFAULT_DIRECTION)))
      );
    }

    List<SortOrder> orders = parseSortOrders(sortValues);
    if (orders.size() > MAX_SORT_ORDERS) {
      throw invalidRequest(
        "iam.api.user.search.sort.max",
        "sort may contain at most " + MAX_SORT_ORDERS + " orders",
        MAX_SORT_ORDERS
      );
    }
    return new SortCriteria(withTieBreaker(orders));
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
        "iam.api.user.search.sort.format",
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
        "iam.api.user.search.sort.allowed",
        "sort must use one of: " + allowedSorts,
        allowedSorts
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

  private static void validateRange(String label, OffsetDateTime from, OffsetDateTime to) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw invalidRequest(
        "iam.api.user.search.date.range",
        label + " range must have from before to",
        label
      );
    }
  }

  @SafeVarargs
  private static List<String> normalizedValues(List<String>... values) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (List<String> valueList : values) {
      if (valueList == null) {
        continue;
      }
      valueList
        .stream()
        .map(AdminListUsersRequest::blankToNull)
        .filter(value -> value != null)
        .forEach(normalized::add);
    }
    return List.copyOf(normalized);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
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
        "iam.api.user.search.direction.pattern",
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
