package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

public interface CategoryOrderingStrategy {
  CategorySystemDescriptor descriptor();

  void validatePlacement(SparseOrderTarget target);

  default String systemKey() {
    return descriptor().systemKey();
  }

  default CategoryCapabilityMetadata capabilityMetadata() {
    return CategoryCapabilityMetadata.from(descriptor());
  }
}
