package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.Objects;
import org.springframework.lang.Nullable;

public class RankedSparseOrderingStrategy implements CategoryOrderingStrategy {

  private final CategorySystemDescriptor descriptor;
  private final SparseRankOperations rankOperations;

  public RankedSparseOrderingStrategy(
    CategorySystemDescriptor descriptor,
    SparseRankOperations rankOperations
  ) {
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    this.rankOperations = Objects.requireNonNull(rankOperations, "rankOperations");
    if (!descriptor.ranking().rankingEnabled()) {
      throw new IllegalArgumentException("Ranked strategy requires ranking to be enabled");
    }
  }

  @Override
  public CategorySystemDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public void validatePlacement(SparseOrderTarget target) {
    Objects.requireNonNull(target, "target");
    if (!descriptor.ranking().placementModes().contains(target.mode())) {
      throw new IllegalArgumentException("Placement mode is not supported: " + target.mode());
    }
  }

  public long allocateRank(@Nullable Long parentId, SparseOrderTarget target) {
    validatePlacement(target);
    return rankOperations.allocateRank(descriptor, parentId, target);
  }

  public RankAssignmentResult assignRank(
    Long categoryId,
    @Nullable Long parentId,
    SparseOrderTarget target
  ) {
    validatePlacement(target);
    validateTargetDoesNotReferenceMovedCategory(categoryId, target);
    return rankOperations.assignRank(descriptor, categoryId, parentId, target);
  }

  private static void validateTargetDoesNotReferenceMovedCategory(
    Long categoryId,
    SparseOrderTarget target
  ) {
    Objects.requireNonNull(categoryId, "categoryId");
    if (categoryId.equals(target.beforeId()) || categoryId.equals(target.afterId())) {
      throw new IllegalArgumentException("Placement target must not be the moved category");
    }
  }
}
