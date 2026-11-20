package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import org.springframework.lang.Nullable;

public record RankAssignmentResult(
  Long categoryId,
  @Nullable Long parentId,
  long assignedRank,
  PlacementMode placementMode,
  boolean rebalancePerformed,
  long updatedRows
) {
  public RankAssignmentResult {
    java.util.Objects.requireNonNull(categoryId, "categoryId");
    java.util.Objects.requireNonNull(placementMode, "placementMode");
    if (updatedRows < 0) {
      throw new IllegalArgumentException("updatedRows must be zero or greater");
    }
  }
}
