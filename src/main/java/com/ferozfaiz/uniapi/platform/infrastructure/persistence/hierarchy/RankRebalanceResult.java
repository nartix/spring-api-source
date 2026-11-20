package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import org.springframework.lang.Nullable;

public record RankRebalanceResult(
  String systemKey,
  @Nullable Long parentId,
  long rowCount,
  long startingRank,
  long rankStep,
  long updatedRows
) {
  public RankRebalanceResult {
    systemKey = SqlIdentifiers.requireText(systemKey, "systemKey");
    requireNonNegative(rowCount, "rowCount");
    requirePositive(startingRank, "startingRank");
    requirePositive(rankStep, "rankStep");
    requireNonNegative(updatedRows, "updatedRows");
  }

  private static void requirePositive(long value, String fieldName) {
    if (value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than zero");
    }
  }

  private static void requireNonNegative(long value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must be zero or greater");
    }
  }
}
