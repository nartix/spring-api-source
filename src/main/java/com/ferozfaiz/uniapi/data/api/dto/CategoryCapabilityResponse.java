package com.ferozfaiz.uniapi.data.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Ordering capabilities for the data category API surface")
public record CategoryCapabilityResponse(
  @Schema(description = "Whether sparse sibling ranking is enabled", example = "true")
  boolean rankingEnabled,
  @Schema(description = "Accepted placement modes for create, move, and reorder requests")
  List<String> placementModes,
  @Schema(description = "Whether category responses expose sortRank", example = "true")
  boolean sortRankExposed,
  @Schema(description = "Fallback order metadata for unranked systems")
  List<FallbackOrderResponse> fallbackOrder
) {
  public CategoryCapabilityResponse {
    placementModes = List.copyOf(placementModes);
    fallbackOrder = List.copyOf(fallbackOrder);
  }

  public record FallbackOrderResponse(String property, String direction) {}
}
