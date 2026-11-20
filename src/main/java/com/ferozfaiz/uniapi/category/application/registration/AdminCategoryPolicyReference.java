package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Optional;

public record AdminCategoryPolicyReference(
  AdminCategoryPolicyObligation obligation,
  AdminCategoryLogicalPolicyId logicalId
) {
  public AdminCategoryPolicyReference {
    if (!isKnownObligation(obligation) || logicalId == null) {
      throw new AdminCategoryRegistrationValidationException(
        new AdminCategoryValidationDiagnostic(
          "<unavailable>",
          AdminCategoryValidationCategory.INVALID_POLICY_REFERENCE,
          Optional.of(AdminCategoryValidationComponent.POLICY_REFERENCES),
          Optional.empty(),
          Optional.empty()
        )
      );
    }
  }

  private static boolean isKnownObligation(AdminCategoryPolicyObligation obligation) {
    for (AdminCategoryPolicyObligation known : AdminCategoryPolicyObligation.values()) {
      if (known == obligation) {
        return true;
      }
    }
    return false;
  }
}
