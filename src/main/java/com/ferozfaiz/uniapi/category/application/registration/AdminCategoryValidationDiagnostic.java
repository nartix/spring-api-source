package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AdminCategoryValidationDiagnostic
  implements Comparable<AdminCategoryValidationDiagnostic>
{

  private static final Pattern CANONICAL_KEY = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

  private final String safeSystemKey;
  private final AdminCategoryValidationCategory category;
  private final Optional<AdminCategoryValidationComponent> component;
  private final Optional<AdminCategoryValidationCapability> capability;
  private final Optional<AdminCategoryPolicyObligation> policyObligation;

  public AdminCategoryValidationDiagnostic(
    String safeSystemKey,
    AdminCategoryValidationCategory category,
    Optional<AdminCategoryValidationComponent> component,
    Optional<AdminCategoryValidationCapability> capability,
    Optional<AdminCategoryPolicyReference> policyReference
  ) {
    if (
      !isSafeSystemKey(safeSystemKey) ||
      !isKnownCategory(category) ||
      component == null ||
      capability == null ||
      policyReference == null ||
      component.filter(value -> !isKnownComponent(value)).isPresent() ||
      capability.filter(value -> !isKnownCapability(value)).isPresent() ||
      policyReference.filter(value -> !isValidPolicyReference(value)).isPresent()
    ) {
      throw invalidDiagnostic();
    }
    this.safeSystemKey = safeSystemKey;
    this.category = category;
    this.component = component;
    this.capability = capability;
    this.policyObligation = policyReference.map(AdminCategoryPolicyReference::obligation);
  }

  public String safeSystemKey() {
    return safeSystemKey;
  }

  public AdminCategoryValidationCategory category() {
    return category;
  }

  public Optional<AdminCategoryValidationComponent> component() {
    return component;
  }

  public Optional<AdminCategoryValidationCapability> capability() {
    return capability;
  }

  public Optional<AdminCategoryPolicyObligation> policyObligation() {
    return policyObligation;
  }

  /**
   * Phase 1 source-compatibility projection. Logical policy identifiers are intentionally not
   * retained by diagnostics.
   */
  public Optional<AdminCategoryPolicyReference> policyReference() {
    return Optional.empty();
  }

  @Override
  public int compareTo(AdminCategoryValidationDiagnostic other) {
    return AdminCategoryRegistrationDiagnostics.ORDER.compare(this, other);
  }

  public AdminCategoryRegistrationValidationException asException() {
    return new AdminCategoryRegistrationValidationException(this);
  }

  @Override
  public boolean equals(Object other) {
    return (
      this == other ||
      (other instanceof AdminCategoryValidationDiagnostic that &&
        safeSystemKey.equals(that.safeSystemKey) &&
        category == that.category &&
        component.equals(that.component) &&
        capability.equals(that.capability) &&
        policyObligation.equals(that.policyObligation))
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(safeSystemKey, category, component, capability, policyObligation);
  }

  @Override
  public String toString() {
    return AdminCategoryRegistrationDiagnostics.renderSingle(this);
  }

  private static boolean isSafeSystemKey(String value) {
    return (
      AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY.equals(value) ||
      (value != null && value.length() <= 63 && CANONICAL_KEY.matcher(value).matches())
    );
  }

  private static boolean isKnownCategory(AdminCategoryValidationCategory value) {
    for (AdminCategoryValidationCategory known : AdminCategoryValidationCategory.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownComponent(AdminCategoryValidationComponent value) {
    for (AdminCategoryValidationComponent known : AdminCategoryValidationComponent.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isKnownCapability(AdminCategoryValidationCapability value) {
    for (AdminCategoryValidationCapability known : AdminCategoryValidationCapability.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isValidPolicyReference(AdminCategoryPolicyReference reference) {
    try {
      if (reference == null || !isKnownObligation(reference.obligation())) {
        return false;
      }
      AdminCategoryLogicalPolicyId logicalId = reference.logicalId();
      return (
        logicalId != null &&
        isCanonicalKey(logicalId.moduleKey()) &&
        isCanonicalKey(logicalId.policyKey())
      );
    } catch (RuntimeException failure) {
      return false;
    }
  }

  private static boolean isKnownObligation(AdminCategoryPolicyObligation value) {
    for (AdminCategoryPolicyObligation known : AdminCategoryPolicyObligation.values()) {
      if (known == value) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCanonicalKey(String value) {
    return value != null && value.length() <= 63 && CANONICAL_KEY.matcher(value).matches();
  }

  private static AdminCategoryRegistrationValidationException invalidDiagnostic() {
    return new AdminCategoryRegistrationValidationException(
      new AdminCategoryValidationDiagnostic(
        AdminCategoryRegistrationDiagnostics.UNAVAILABLE_SYSTEM_KEY,
        AdminCategoryValidationCategory.INVALID_COLLECTION,
        Optional.of(AdminCategoryValidationComponent.REGISTRATION),
        Optional.empty(),
        Optional.empty()
      )
    );
  }
}
