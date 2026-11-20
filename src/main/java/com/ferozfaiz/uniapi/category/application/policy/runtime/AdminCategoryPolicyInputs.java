package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.List;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public final class AdminCategoryPolicyInputs {

  private AdminCategoryPolicyInputs() {}

  private static void requireContext(
    AdminCategorySystemDefinition definition,
    Operation operation
  ) {
    Objects.requireNonNull(definition, "definition must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }

  public record AdminCategoryGeneralValidationInput(
    AdminCategorySystemDefinition definition,
    Operation operation,
    Long resourceId,
    Long parentId
  ) {
    public AdminCategoryGeneralValidationInput {
      requireContext(definition, operation);
    }
  }

  public record AdminCategoryListValidationInput(
    AdminCategorySystemDefinition definition,
    Integer page,
    Integer size,
    String parentId,
    Boolean active,
    String q,
    String slug,
    List<String> sort,
    String direction
  ) {
    public AdminCategoryListValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
      sort = sort == null ? List.of() : List.copyOf(sort);
    }
  }

  public record AdminCategoryTreeValidationInput(
    AdminCategorySystemDefinition definition,
    Integer page,
    Integer size,
    List<String> sort,
    String direction
  ) {
    public AdminCategoryTreeValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
      sort = sort == null ? List.of() : List.copyOf(sort);
    }
  }

  public record AdminCategoryParentOptionsValidationInput(
    AdminCategorySystemDefinition definition,
    String q,
    Integer limit,
    String cursor,
    List<String> includeIds,
    String excludeSubtreeId,
    String includeInactive
  ) {
    public AdminCategoryParentOptionsValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
      includeIds = includeIds == null ? List.of() : List.copyOf(includeIds);
    }
  }

  public record AdminCategoryCreateValidationInput(
    AdminCategorySystemDefinition definition,
    Long parentId,
    Long beforeId,
    Long afterId,
    Integer targetIndex,
    String name,
    String slug,
    Boolean active
  ) {
    public AdminCategoryCreateValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
    }
  }

  public record AdminCategoryReplaceValidationInput(
    AdminCategorySystemDefinition definition,
    Long id,
    Long parentId,
    String name,
    String slug,
    Boolean active
  ) {
    public AdminCategoryReplaceValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
    }
  }

  public record AdminCategoryPatchValidationInput(
    AdminCategorySystemDefinition definition,
    Long id,
    Long parentId,
    boolean parentIdPresent,
    String name,
    boolean namePresent,
    String slug,
    boolean slugPresent,
    Boolean active,
    boolean activePresent
  ) {
    public AdminCategoryPatchValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
    }
  }

  public record AdminCategoryPlacementValidationInput(
    AdminCategorySystemDefinition definition,
    Operation operation,
    Long parentId,
    Long beforeId,
    Long afterId,
    Integer targetIndex
  ) {
    public AdminCategoryPlacementValidationInput {
      requireContext(definition, operation);
    }
  }

  public record AdminCategoryHierarchyValidationInput(
    AdminCategorySystemDefinition definition,
    Operation operation,
    Long resourceId,
    Long parentId,
    Long excludeSubtreeId
  ) {
    public AdminCategoryHierarchyValidationInput {
      requireContext(definition, operation);
    }
  }

  public record AdminCategoryDeletionValidationInput(
    AdminCategorySystemDefinition definition,
    Long id
  ) {
    public AdminCategoryDeletionValidationInput {
      Objects.requireNonNull(definition, "definition must not be null");
    }
  }

  public record AdminCategoryLifecycleValidationInput(
    AdminCategorySystemDefinition definition,
    Operation operation,
    Long id
  ) {
    public AdminCategoryLifecycleValidationInput {
      requireContext(definition, operation);
    }
  }
}
