package com.ferozfaiz.uniapi.category.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Runtime capabilities for a registered category system")
public record AdminCategoryCapabilityResponse(
  boolean rankingEnabled,
  List<String> placementModes,
  boolean sortRankExposed,
  List<FallbackOrderResponse> fallbackOrder,
  List<String> supportedOperations,
  AdminCategoryHierarchyCapabilityResponse hierarchy,
  AdminCategoryOperationalLimitsResponse limits,
  AdminCategoryLifecycleCapabilityResponse lifecycle,
  String identifierProfile,
  List<String> fields,
  List<String> searchableFields,
  List<String> allowedFilters,
  List<String> allowedSorts,
  AdminCategoryOrderingCapabilityResponse ordering
) {
  public AdminCategoryCapabilityResponse {
    placementModes = List.copyOf(placementModes);
    fallbackOrder = List.copyOf(fallbackOrder);
    supportedOperations = List.copyOf(supportedOperations);
    fields = List.copyOf(fields);
    searchableFields = List.copyOf(searchableFields);
    allowedFilters = List.copyOf(allowedFilters);
    allowedSorts = List.copyOf(allowedSorts);
  }

  public record FallbackOrderResponse(String property, String direction) {}

  public record AdminCategoryHierarchyCapabilityResponse(
    boolean enabled,
    List<String> readOperations,
    List<String> mutationOperations,
    String rootModel
  ) {
    public AdminCategoryHierarchyCapabilityResponse {
      readOperations = List.copyOf(readOperations);
      mutationOperations = List.copyOf(mutationOperations);
    }
  }

  public record AdminCategoryOperationalLimitsResponse(
    Long pageSize,
    Long searchInputLength,
    Long filterSelections,
    Long sortSelections,
    Long treeSize,
    Long parentOptionPageSize,
    Long includeIdCount,
    Long hierarchyDepth
  ) {}

  public record AdminCategoryLifecycleCapabilityResponse(
    boolean enabled,
    List<String> states,
    List<String> actions
  ) {
    public AdminCategoryLifecycleCapabilityResponse {
      states = List.copyOf(states);
      actions = List.copyOf(actions);
    }
  }

  public record AdminCategoryOrderingCapabilityResponse(
    boolean orderingEnabled,
    boolean rankingEnabled,
    boolean rankExposed,
    boolean reorderSupported,
    List<String> placementModes,
    List<FallbackOrderResponse> fallbackOrder
  ) {
    public AdminCategoryOrderingCapabilityResponse {
      placementModes = List.copyOf(placementModes);
      fallbackOrder = List.copyOf(fallbackOrder);
    }
  }
}
