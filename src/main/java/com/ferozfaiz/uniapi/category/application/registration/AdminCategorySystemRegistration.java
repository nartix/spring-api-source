package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Optional;

public final class AdminCategorySystemRegistration {

  private final Optional<AdminCategorySystemDefinition> definition;
  private final Optional<AdminCategoryRuntimeBinding> runtimeBinding;

  public AdminCategorySystemRegistration(
    Optional<AdminCategorySystemDefinition> definition,
    Optional<AdminCategoryRuntimeBinding> runtimeBinding
  ) {
    if (definition == null) {
      throw invalidSlot("<unavailable>", AdminCategoryValidationComponent.DEFINITION);
    }
    String safeSystemKey = definition
      .map(AdminCategorySystemDefinition::systemKey)
      .orElse("<unavailable>");
    if (runtimeBinding == null) {
      throw invalidSlot(safeSystemKey, AdminCategoryValidationComponent.REGISTRATION);
    }
    this.definition = definition;
    this.runtimeBinding = runtimeBinding;
  }

  public static AdminCategorySystemRegistration of(
    AdminCategorySystemDefinition definition,
    AdminCategoryRuntimeBinding runtimeBinding
  ) {
    if (definition == null) {
      throw invalidSlot("<unavailable>", AdminCategoryValidationComponent.DEFINITION);
    }
    if (runtimeBinding == null) {
      throw invalidSlot(definition.systemKey(), AdminCategoryValidationComponent.REGISTRATION);
    }
    return new AdminCategorySystemRegistration(
      Optional.of(definition),
      Optional.of(runtimeBinding)
    );
  }

  public Optional<AdminCategorySystemDefinition> definition() {
    return definition;
  }

  public Optional<AdminCategoryRuntimeBinding> runtimeBinding() {
    return runtimeBinding;
  }

  public Optional<String> systemKey() {
    return definition.map(AdminCategorySystemDefinition::systemKey);
  }

  private static AdminCategoryRegistrationValidationException invalidSlot(
    String safeSystemKey,
    AdminCategoryValidationComponent component
  ) {
    return new AdminCategoryRegistrationValidationException(
      new AdminCategoryValidationDiagnostic(
        safeSystemKey,
        AdminCategoryValidationCategory.INVALID_COMPONENT_SLOT,
        Optional.of(component),
        Optional.empty(),
        Optional.empty()
      )
    );
  }
}
