package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;

public class UnrankedFallbackOrderingStrategy implements CategoryOrderingStrategy {

  private final CategorySystemDescriptor descriptor;

  public UnrankedFallbackOrderingStrategy(CategorySystemDescriptor descriptor) {
    this.descriptor = java.util.Objects.requireNonNull(descriptor, "descriptor");
    if (descriptor.ranking().rankingEnabled()) {
      throw new IllegalArgumentException("Unranked strategy requires ranking to be disabled");
    }
  }

  @Override
  public CategorySystemDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public void validatePlacement(SparseOrderTarget target) {
    java.util.Objects.requireNonNull(target, "target");
    if (!target.hasExplicitPlacement()) {
      return;
    }
    throw new InvalidRequestException(
      "platform.infrastructure.category.ranking.disabled",
      "ranking is disabled for category system " + descriptor.systemKey(),
      descriptor.systemKey()
    );
  }

  public FallbackOrderDescriptor fallbackOrder() {
    return descriptor.fallbackOrder();
  }
}
