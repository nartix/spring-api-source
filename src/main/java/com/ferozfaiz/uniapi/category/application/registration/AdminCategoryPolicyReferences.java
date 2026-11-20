package com.ferozfaiz.uniapi.category.application.registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdminCategoryPolicyReferences {

  private final List<AdminCategoryPolicyReference> references;
  private final Map<
    AdminCategoryPolicyObligation,
    AdminCategoryPolicyReference
  > referencesByObligation;

  public AdminCategoryPolicyReferences(Collection<AdminCategoryPolicyReference> references) {
    if (references == null) {
      throw failure(AdminCategoryValidationCategory.INVALID_COLLECTION, Optional.empty());
    }

    var snapshot = new ArrayList<AdminCategoryPolicyReference>(references.size());
    var exactReferences = new HashSet<AdminCategoryPolicyReference>();
    var obligations = new HashSet<AdminCategoryPolicyObligation>();
    for (AdminCategoryPolicyReference reference : references) {
      if (reference == null) {
        throw failure(AdminCategoryValidationCategory.INVALID_COLLECTION, Optional.empty());
      }
      if (!exactReferences.add(reference) || !obligations.add(reference.obligation())) {
        throw failure(
          AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
          Optional.of(reference)
        );
      }
      snapshot.add(reference);
    }

    snapshot.sort((left, right) -> {
      int comparison = left.obligation().code().compareTo(right.obligation().code());
      if (comparison != 0) {
        return comparison;
      }
      comparison = left.logicalId().moduleKey().compareTo(right.logicalId().moduleKey());
      return comparison != 0
        ? comparison
        : left.logicalId().policyKey().compareTo(right.logicalId().policyKey());
    });
    this.references = List.copyOf(snapshot);
    var lookup = new EnumMap<AdminCategoryPolicyObligation, AdminCategoryPolicyReference>(
      AdminCategoryPolicyObligation.class
    );
    for (AdminCategoryPolicyReference reference : snapshot) {
      lookup.put(reference.obligation(), reference);
    }
    this.referencesByObligation = Map.copyOf(lookup);
  }

  public Optional<AdminCategoryPolicyReference> find(AdminCategoryPolicyObligation obligation) {
    if (obligation == null) {
      throw failure(AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE, Optional.empty());
    }
    return Optional.ofNullable(referencesByObligation.get(obligation));
  }

  public List<AdminCategoryPolicyReference> references() {
    return references;
  }

  @Override
  public boolean equals(Object other) {
    return (
      this == other ||
      (other instanceof AdminCategoryPolicyReferences that && references.equals(that.references))
    );
  }

  @Override
  public int hashCode() {
    return references.hashCode();
  }

  private static AdminCategoryRegistrationValidationException failure(
    AdminCategoryValidationCategory category,
    Optional<AdminCategoryPolicyReference> policyReference
  ) {
    return new AdminCategoryRegistrationValidationException(
      new AdminCategoryValidationDiagnostic(
        "<unavailable>",
        category,
        Optional.of(AdminCategoryValidationComponent.POLICY_REFERENCES),
        Optional.empty(),
        policyReference
      )
    );
  }
}
