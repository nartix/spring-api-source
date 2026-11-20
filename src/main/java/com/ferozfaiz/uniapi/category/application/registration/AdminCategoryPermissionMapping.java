package com.ferozfaiz.uniapi.category.application.registration;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AdminCategoryPermissionMapping {

  private static final Pattern PERMISSION_PATTERN = Pattern.compile("[A-Za-z0-9:_*-]{1,86}");

  private final List<Binding> bindings;
  private final Map<Operation, String> permissionsByOperation;

  public AdminCategoryPermissionMapping(Collection<Binding> bindings) {
    if (bindings == null) {
      throw failure(AdminCategoryValidationCategory.INVALID_COLLECTION, Optional.empty());
    }

    var snapshot = new ArrayList<Binding>(bindings.size());
    var operations = new HashSet<Operation>();
    for (Binding binding : bindings) {
      if (binding == null) {
        throw failure(AdminCategoryValidationCategory.INVALID_COLLECTION, Optional.empty());
      }
      if (!operations.add(binding.operation())) {
        throw failure(
          AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
          Optional.of(capability(binding.operation()))
        );
      }
      snapshot.add(binding);
    }

    snapshot.sort((left, right) -> left.operation().code().compareTo(right.operation().code()));
    this.bindings = List.copyOf(snapshot);
    var lookup = new EnumMap<Operation, String>(Operation.class);
    for (Binding binding : snapshot) {
      lookup.put(binding.operation(), binding.permission());
    }
    this.permissionsByOperation = Map.copyOf(lookup);
  }

  public Optional<String> permissionFor(Operation operation) {
    if (operation == null) {
      throw failure(AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING, Optional.empty());
    }
    return Optional.ofNullable(permissionsByOperation.get(operation));
  }

  public List<Binding> bindings() {
    return bindings;
  }

  @Override
  public boolean equals(Object other) {
    return (
      this == other ||
      (other instanceof AdminCategoryPermissionMapping that && bindings.equals(that.bindings))
    );
  }

  @Override
  public int hashCode() {
    return bindings.hashCode();
  }

  public record Binding(Operation operation, String permission) {
    public Binding {
      if (!isKnownOperation(operation)) {
        throw failure(AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING, Optional.empty());
      }
      if (permission == null || !PERMISSION_PATTERN.matcher(permission).matches()) {
        throw failure(
          AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
          Optional.of(capability(operation))
        );
      }
    }
  }

  private static AdminCategoryRegistrationValidationException failure(
    AdminCategoryValidationCategory category,
    Optional<AdminCategoryValidationCapability> capability
  ) {
    return new AdminCategoryRegistrationValidationException(
      new AdminCategoryValidationDiagnostic(
        "<unavailable>",
        category,
        Optional.of(AdminCategoryValidationComponent.PERMISSION_MAPPING),
        capability,
        Optional.empty()
      )
    );
  }

  private static AdminCategoryValidationCapability capability(Operation operation) {
    return switch (Objects.requireNonNull(operation)) {
      case CAPABILITIES -> AdminCategoryValidationCapability.CAPABILITIES;
      case LIST -> AdminCategoryValidationCapability.LIST;
      case TREE -> AdminCategoryValidationCapability.TREE;
      case PARENT_OPTIONS -> AdminCategoryValidationCapability.PARENT_OPTIONS;
      case GET -> AdminCategoryValidationCapability.GET;
      case BREADCRUMB -> AdminCategoryValidationCapability.BREADCRUMB;
      case CREATE -> AdminCategoryValidationCapability.CREATE;
      case REPLACE -> AdminCategoryValidationCapability.REPLACE;
      case PATCH -> AdminCategoryValidationCapability.PATCH;
      case DELETE -> AdminCategoryValidationCapability.DELETE;
      case MOVE -> AdminCategoryValidationCapability.MOVE;
      case REORDER -> AdminCategoryValidationCapability.REORDER;
      case ACTIVATE -> AdminCategoryValidationCapability.ACTIVATE;
      case DEACTIVATE -> AdminCategoryValidationCapability.DEACTIVATE;
    };
  }

  private static boolean isKnownOperation(Operation operation) {
    for (Operation known : Operation.values()) {
      if (known == operation) {
        return true;
      }
    }
    return false;
  }
}
