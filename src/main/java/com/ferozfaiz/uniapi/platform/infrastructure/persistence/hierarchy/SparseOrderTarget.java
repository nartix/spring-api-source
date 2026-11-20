package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import org.springframework.lang.Nullable;

public record SparseOrderTarget(
  PlacementMode mode,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Nullable Integer targetIndex
) {
  public SparseOrderTarget {
    java.util.Objects.requireNonNull(mode, "mode");
    int explicitFields =
      (beforeId == null ? 0 : 1) + (afterId == null ? 0 : 1) + (targetIndex == null ? 0 : 1);
    if (mode == PlacementMode.APPEND && explicitFields != 0) {
      throw new IllegalArgumentException("append placement must not include explicit targets");
    }
    if (mode != PlacementMode.APPEND && explicitFields != 1) {
      throw new IllegalArgumentException("explicit placement requires exactly one target field");
    }
    if (targetIndex != null && targetIndex < 0) {
      throw new IllegalArgumentException("targetIndex must be zero or greater");
    }
  }

  public static SparseOrderTarget append() {
    return new SparseOrderTarget(PlacementMode.APPEND, null, null, null);
  }

  public static SparseOrderTarget from(
    @Nullable Long beforeId,
    @Nullable Long afterId,
    @Nullable Integer targetIndex
  ) {
    int explicitFields =
      (beforeId == null ? 0 : 1) + (afterId == null ? 0 : 1) + (targetIndex == null ? 0 : 1);
    if (explicitFields == 0) {
      return append();
    }
    if (explicitFields > 1) {
      throw new IllegalArgumentException("placement must include exactly one explicit target");
    }
    if (beforeId != null) {
      return new SparseOrderTarget(PlacementMode.BEFORE_ID, beforeId, null, null);
    }
    if (afterId != null) {
      return new SparseOrderTarget(PlacementMode.AFTER_ID, null, afterId, null);
    }
    return new SparseOrderTarget(PlacementMode.TARGET_INDEX, null, null, targetIndex);
  }

  public boolean hasExplicitPlacement() {
    return mode != PlacementMode.APPEND;
  }
}
