package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.Optional;

public record CategorySystemDescriptor(
  String systemKey,
  String owningModule,
  ClosureTableDescriptor closureTableDescriptor,
  RankingCapabilityDescriptor ranking,
  FallbackOrderDescriptor fallbackOrder,
  Optional<SparseRankTableDescriptor> rankTableDescriptor
) {
  public CategorySystemDescriptor {
    systemKey = SqlIdentifiers.requireText(systemKey, "systemKey");
    owningModule = SqlIdentifiers.requireText(owningModule, "owningModule");
    java.util.Objects.requireNonNull(closureTableDescriptor, "closureTableDescriptor");
    java.util.Objects.requireNonNull(ranking, "ranking");
    java.util.Objects.requireNonNull(fallbackOrder, "fallbackOrder");
    rankTableDescriptor = java.util.Objects.requireNonNull(
      rankTableDescriptor,
      "rankTableDescriptor"
    );
    if (ranking.rankingEnabled() && rankTableDescriptor.isEmpty()) {
      throw new IllegalArgumentException("rankTableDescriptor is required when ranking is enabled");
    }
    if (!ranking.rankingEnabled() && rankTableDescriptor.isPresent()) {
      throw new IllegalArgumentException(
        "rankTableDescriptor must be absent when ranking is disabled"
      );
    }
  }
}
