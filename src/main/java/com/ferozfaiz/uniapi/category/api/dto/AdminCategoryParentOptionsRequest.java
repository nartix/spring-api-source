package com.ferozfaiz.uniapi.category.api.dto;

import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryParentOptionsPolicy;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Search and hydration parameters for generic admin category parent options")
public record AdminCategoryParentOptionsRequest(
  @Schema(description = "Raw search text. Values longer than 120 characters are rejected.")
  String q,
  @Schema(description = "Total option limit after selected and ordinary candidates are merged.")
  Integer limit,
  @Schema(description = "Opaque continuation cursor returned by a previous response") String cursor,
  @Schema(description = "Repeated selected category IDs to hydrate before ordinary candidates")
  List<String> includeIds,
  @Schema(description = "Category ID whose self and descendants should be omitted")
  String excludeSubtreeId,
  @Schema(description = "Defaults to true. false excludes inactive ordinary candidates only.")
  String includeInactive
) {
  public AdminCategoryParentOptionsQuery toQuery() {
    return new AdminCategoryParentOptionsQuery(
      AdminCategoryParentOptionsPolicy.normalizeSearch(q),
      AdminCategoryParentOptionsPolicy.resolveLimit(limit),
      AdminCategoryParentOptionsPolicy.blankToNull(cursor),
      AdminCategoryParentOptionsPolicy.normalizeIncludeIds(includeIds),
      AdminCategoryParentOptionsPolicy.normalizeExcludeSubtreeId(excludeSubtreeId),
      AdminCategoryParentOptionsPolicy.resolveIncludeInactive(includeInactive)
    );
  }
}
