package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.List;

public record CategoryCapabilityMetadata(
  boolean rankingEnabled,
  List<PlacementMode> placementModes,
  boolean sortRankExposed,
  List<FallbackOrder> fallbackOrder
) {
  public CategoryCapabilityMetadata {
    placementModes = List.copyOf(placementModes);
    fallbackOrder = List.copyOf(fallbackOrder);
  }

  public static CategoryCapabilityMetadata from(CategorySystemDescriptor descriptor) {
    RankingCapabilityDescriptor ranking = descriptor.ranking();
    return new CategoryCapabilityMetadata(
      ranking.rankingEnabled(),
      ranking.placementModes(),
      ranking.sortRankExposed(),
      fallbackOrderFor(descriptor, ranking)
    );
  }

  private static List<FallbackOrder> fallbackOrderFor(
    CategorySystemDescriptor descriptor,
    RankingCapabilityDescriptor ranking
  ) {
    if (ranking.rankingEnabled() && ranking.sortRankExposed()) {
      return List.of();
    }
    return descriptor
      .fallbackOrder()
      .orders()
      .stream()
      .map(order -> new FallbackOrder(order.property(), order.direction()))
      .toList();
  }

  public record FallbackOrder(String property, FallbackOrderDescriptor.Direction direction) {}
}
