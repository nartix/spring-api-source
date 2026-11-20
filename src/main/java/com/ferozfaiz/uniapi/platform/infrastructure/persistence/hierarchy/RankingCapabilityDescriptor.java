package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.List;
import org.springframework.lang.Nullable;

public record RankingCapabilityDescriptor(
  boolean rankingEnabled,
  @Nullable String columnName,
  long startingRank,
  long rankStep,
  long maxSyncRebalanceRows,
  DisabledRankingRequestPolicy disabledRequestPolicy,
  List<PlacementMode> placementModes,
  boolean sortRankExposed
) {
  public RankingCapabilityDescriptor {
    disabledRequestPolicy = java.util.Objects.requireNonNull(
      disabledRequestPolicy,
      "disabledRequestPolicy"
    );
    placementModes = List.copyOf(placementModes);
    if (placementModes.isEmpty()) {
      throw new IllegalArgumentException("placementModes must contain at least APPEND");
    }
    if (!placementModes.contains(PlacementMode.APPEND)) {
      throw new IllegalArgumentException("placementModes must include APPEND");
    }
    if (rankingEnabled) {
      columnName = SqlIdentifiers.requireIdentifier(columnName, "columnName");
      requirePositive(startingRank, "startingRank");
      requirePositive(rankStep, "rankStep");
      requirePositive(maxSyncRebalanceRows, "maxSyncRebalanceRows");
    } else {
      columnName = null;
      if (placementModes.size() != 1 || placementModes.getFirst() != PlacementMode.APPEND) {
        throw new IllegalArgumentException("disabled ranking only supports APPEND placement");
      }
      if (sortRankExposed) {
        throw new IllegalArgumentException("disabled ranking must not expose sortRank");
      }
    }
  }

  public static RankingCapabilityDescriptor ranked(
    String columnName,
    long startingRank,
    long rankStep,
    long maxSyncRebalanceRows,
    DisabledRankingRequestPolicy disabledRequestPolicy,
    List<PlacementMode> placementModes,
    boolean sortRankExposed
  ) {
    return new RankingCapabilityDescriptor(
      true,
      columnName,
      startingRank,
      rankStep,
      maxSyncRebalanceRows,
      disabledRequestPolicy,
      placementModes,
      sortRankExposed
    );
  }

  public static RankingCapabilityDescriptor unranked(
    DisabledRankingRequestPolicy disabledRequestPolicy
  ) {
    return new RankingCapabilityDescriptor(
      false,
      null,
      1_000_000_000L,
      1_000_000_000L,
      1_000L,
      disabledRequestPolicy,
      List.of(PlacementMode.APPEND),
      false
    );
  }

  private static void requirePositive(long value, String fieldName) {
    if (value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than zero");
    }
  }
}
