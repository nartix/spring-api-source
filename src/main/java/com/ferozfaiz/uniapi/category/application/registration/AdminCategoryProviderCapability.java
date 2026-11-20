package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Optional;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public enum AdminCategoryProviderCapability {
  CATEGORY_READ,
  HIERARCHY_READ,
  BASIC_MUTATION,
  HIERARCHY_MUTATION,
  ORDERING,
  LIFECYCLE;

  public static Optional<AdminCategoryProviderCapability> forOperation(
    AdminCategorySystemDefinition.Operation operation
  ) {
    if (operation == null) throw new IllegalArgumentException("operation must not be null");
    return switch (operation) {
      case CAPABILITIES -> Optional.empty();
      case LIST, GET -> Optional.of(CATEGORY_READ);
      case TREE, PARENT_OPTIONS, BREADCRUMB -> Optional.of(HIERARCHY_READ);
      case CREATE, REPLACE, PATCH, DELETE -> Optional.of(BASIC_MUTATION);
      case MOVE -> Optional.of(HIERARCHY_MUTATION);
      case REORDER -> Optional.of(ORDERING);
      case ACTIVATE, DEACTIVATE -> Optional.of(LIFECYCLE);
    };
  }
}
