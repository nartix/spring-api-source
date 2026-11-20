package com.ferozfaiz.uniapi.iam.api.dto;

import com.ferozfaiz.uniapi.iam.application.query.AdminListRolesQuery;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Filters, pagination, and sorting options for listing assignable roles")
public record AdminListRolesRequest(
  @Min(value = 0, message = "{iam.api.admin-access-options.search.page.min}")
  @Schema(description = "Zero-based page index", example = "0", defaultValue = "0", minimum = "0")
  Integer page,
  @Min(value = 1, message = "{iam.api.admin-access-options.search.size.min}")
  @Max(value = 100, message = "{iam.api.admin-access-options.search.size.max}")
  @Schema(
    description = "Number of roles per page",
    example = "100",
    defaultValue = "100",
    minimum = "1",
    maximum = "100"
  )
  Integer size,
  @Schema(
    description = "Sort orders. Repeat the parameter for multiple fields, using field,direction.",
    example = "[\"code,asc\", \"id,asc\"]",
    defaultValue = "code,asc"
  )
  List<String> sort,
  @Size(max = 120, message = "{iam.api.admin-access-options.search.query.size}")
  @Schema(description = "Search role code, name, or description", example = "moderator")
  String q,
  @Schema(description = "Filter by active status. Defaults to true.", example = "true")
  Boolean active,
  @Pattern(
    regexp = "(?i)asc|desc",
    message = "{iam.api.admin-access-options.search.direction.pattern}"
  )
  @Schema(
    description = "Fallback sort direction for a single sort field without an inline direction",
    example = "asc",
    defaultValue = "asc",
    allowableValues = { "asc", "desc" }
  )
  String direction
) {
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 100;
  private static final List<String> ALLOWED_SORTS = List.of(
    "id",
    "code",
    "name",
    "active",
    "createdAt",
    "updatedAt"
  );
  private static final List<SortOrder> DEFAULT_SORT = List.of(
    new SortOrder("code", SortDirection.ASC)
  );

  public AdminListRolesQuery toQuery() {
    return new AdminListRolesQuery(
      toPageCriteria(),
      toSortCriteria(),
      blankToNull(q),
      active == null ? Boolean.TRUE : active
    );
  }

  public PageCriteria toPageCriteria() {
    return new PageCriteria(page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
  }

  public SortCriteria toSortCriteria() {
    return AdminAccessOptionSortParser.parse(sort, direction, ALLOWED_SORTS, DEFAULT_SORT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
