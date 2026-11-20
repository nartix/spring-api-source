package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CategorySystemRegistry {

  private final Map<String, CategorySystemDescriptor> descriptors;

  public CategorySystemRegistry(CategorySystemProperties properties) {
    this.descriptors = buildDescriptors(properties);
  }

  public Collection<CategorySystemDescriptor> descriptors() {
    return List.copyOf(descriptors.values());
  }

  public Optional<CategorySystemDescriptor> findDescriptor(String systemKey) {
    String key = SqlIdentifiers.requireText(systemKey, "systemKey");
    return Optional.ofNullable(descriptors.get(key));
  }

  public CategorySystemDescriptor requireDescriptor(String systemKey) {
    String key = SqlIdentifiers.requireText(systemKey, "systemKey");
    CategorySystemDescriptor descriptor = descriptors.get(key);
    if (descriptor == null) {
      throw new IllegalArgumentException("Unknown category system: " + key);
    }
    return descriptor;
  }

  private static Map<String, CategorySystemDescriptor> buildDescriptors(
    CategorySystemProperties properties
  ) {
    Map<String, CategorySystemDescriptor> built = new LinkedHashMap<>();
    properties.getCategorySystems().forEach((key, system) -> {
      String systemKey = SqlIdentifiers.requireText(key, "systemKey");
      if (built.containsKey(systemKey)) {
        throw new IllegalArgumentException("Duplicate category system: " + systemKey);
      }
      built.put(systemKey, toDescriptor(systemKey, system));
    });
    return Map.copyOf(built);
  }

  private static CategorySystemDescriptor toDescriptor(
    String systemKey,
    CategorySystemProperties.SystemProperties system
  ) {
    ClosureTableDescriptor closureTableDescriptor = new ClosureTableDescriptor(
      system.getNodeTable(),
      system.getClosureTable(),
      system.getNodeIdColumn(),
      system.getParentIdColumn(),
      system.getAncestorColumn(),
      system.getDescendantColumn(),
      system.getDepthColumn()
    );
    RankingCapabilityDescriptor ranking = toRanking(system.getRanking());
    Optional<SparseRankTableDescriptor> rankTableDescriptor = ranking.rankingEnabled()
      ? Optional.of(
          new SparseRankTableDescriptor(
            system.getNodeTable(),
            system.getNodeIdColumn(),
            system.getParentIdColumn(),
            ranking.columnName()
          )
        )
      : Optional.empty();

    return new CategorySystemDescriptor(
      systemKey,
      system.getOwningModule(),
      closureTableDescriptor,
      ranking,
      toFallbackOrder(system.getFallbackOrder()),
      rankTableDescriptor
    );
  }

  private static RankingCapabilityDescriptor toRanking(
    CategorySystemProperties.RankingProperties ranking
  ) {
    if (!ranking.isEnabled()) {
      return RankingCapabilityDescriptor.unranked(ranking.getDisabledRequestPolicy());
    }
    return RankingCapabilityDescriptor.ranked(
      ranking.getColumnName(),
      ranking.getStartingRank(),
      ranking.getRankStep(),
      ranking.getMaxSyncRebalanceRows(),
      ranking.getDisabledRequestPolicy(),
      ranking.getPlacementModes(),
      ranking.isSortRankExposed()
    );
  }

  private static FallbackOrderDescriptor toFallbackOrder(
    List<CategorySystemProperties.FallbackOrderProperties> fallbackOrder
  ) {
    return new FallbackOrderDescriptor(
      fallbackOrder
        .stream()
        .map(order ->
          new FallbackOrderDescriptor.Order(
            order.getProperty(),
            order.getColumnName(),
            order.getDirection()
          )
        )
        .toList()
    );
  }
}
