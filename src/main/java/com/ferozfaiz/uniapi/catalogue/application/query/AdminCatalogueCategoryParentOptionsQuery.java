package com.ferozfaiz.uniapi.catalogue.application.query;

import com.ferozfaiz.uniapi.catalogue.application.policy.AdminCategoryParentOptionsPolicy;
import java.util.List;
import java.util.Objects;

public record AdminCatalogueCategoryParentOptionsQuery(
  String q,
  Long numericQuery,
  int limit,
  AdminCatalogueCategoryParentOptionsCursor cursor,
  Long excludeSubtreeId,
  List<Long> includeIds,
  boolean includeInactive
) {
  public AdminCatalogueCategoryParentOptionsQuery {
    includeIds = includeIds == null ? List.of() : List.copyOf(includeIds);
    if (limit < AdminCategoryParentOptionsPolicy.MIN_LIMIT) {
      throw new IllegalArgumentException("limit must be at least 1");
    }
    if (limit > AdminCategoryParentOptionsPolicy.MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be at most 50");
    }
    if (cursor != null) {
      cursor.validateContext(q, includeInactive, excludeSubtreeId);
    }
  }

  public static AdminCatalogueCategoryParentOptionsQuery from(
    String rawSearch,
    Integer rawLimit,
    String rawCursor,
    List<String> rawIncludeIds,
    String rawExcludeSubtreeId,
    String rawIncludeInactive
  ) {
    String q = AdminCategoryParentOptionsPolicy.normalizeSearch(rawSearch);
    int limit = AdminCategoryParentOptionsPolicy.resolveLimit(rawLimit);
    List<Long> includeIds = AdminCategoryParentOptionsPolicy.normalizeIncludeIds(rawIncludeIds);
    Long excludeSubtreeId = AdminCategoryParentOptionsPolicy.normalizeExcludeSubtreeId(
      rawExcludeSubtreeId
    );
    boolean includeInactive = AdminCategoryParentOptionsPolicy.resolveIncludeInactive(
      rawIncludeInactive
    );
    AdminCatalogueCategoryParentOptionsCursor cursor =
      AdminCatalogueCategoryParentOptionsCursor.decode(rawCursor);
    return new AdminCatalogueCategoryParentOptionsQuery(
      q,
      AdminCategoryParentOptionsPolicy.numericSearchCandidate(q),
      limit,
      cursor,
      excludeSubtreeId,
      includeIds,
      includeInactive
    );
  }

  public AdminCatalogueCategoryParentOptionsCursor.Mode mode() {
    return q == null
      ? AdminCatalogueCategoryParentOptionsCursor.Mode.DEFAULT
      : AdminCatalogueCategoryParentOptionsCursor.Mode.SEARCH;
  }

  public boolean hasSameOrdinaryContext(AdminCatalogueCategoryParentOptionsCursor otherCursor) {
    return (
      otherCursor != null &&
      otherCursor.mode() == mode() &&
      Objects.equals(otherCursor.q(), AdminCategoryParentOptionsPolicy.normalizedSearchKey(q)) &&
      otherCursor.includeInactive() == includeInactive &&
      Objects.equals(otherCursor.excludeSubtreeId(), excludeSubtreeId)
    );
  }
}
