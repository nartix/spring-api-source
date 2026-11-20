package com.ferozfaiz.uniapi.catalogue.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Ordering capabilities for a catalogue category API surface")
public record CatalogueCategoryCapabilityResponse(
  @Schema(description = "Whether sparse sibling ranking is enabled", example = "true")
  boolean rankingEnabled,
  @Schema(description = "Accepted placement modes for create, move, and reorder requests")
  List<String> placementModes,
  @Schema(description = "Whether category responses expose sortRank", example = "true")
  boolean sortRankExposed,
  @Schema(description = "Fallback order metadata for unranked systems")
  List<FallbackOrderResponse> fallbackOrder
) {
  public CatalogueCategoryCapabilityResponse {
    placementModes = List.copyOf(placementModes);
    fallbackOrder = List.copyOf(fallbackOrder);
  }

  public record FallbackOrderResponse(String property, String direction) {}
}
