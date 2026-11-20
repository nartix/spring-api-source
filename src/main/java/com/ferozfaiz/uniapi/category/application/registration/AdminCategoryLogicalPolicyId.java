package com.ferozfaiz.uniapi.category.application.registration;

import java.util.Optional;
import java.util.regex.Pattern;

public record AdminCategoryLogicalPolicyId(String moduleKey, String policyKey) {
  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

  public AdminCategoryLogicalPolicyId {
    if (!isCanonicalKey(moduleKey) || !isCanonicalKey(policyKey)) {
      throw failure();
    }
  }

  public static AdminCategoryLogicalPolicyId parse(String value) {
    if (value == null) {
      throw failure();
    }
    int separator = value.indexOf(':');
    if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
      throw failure();
    }
    return new AdminCategoryLogicalPolicyId(
      value.substring(0, separator),
      value.substring(separator + 1)
    );
  }

  public String value() {
    return moduleKey + ":" + policyKey;
  }

  private static boolean isCanonicalKey(String value) {
    return (value != null && value.length() <= 63 && KEY_PATTERN.matcher(value).matches());
  }

  private static AdminCategoryRegistrationValidationException failure() {
    return new AdminCategoryRegistrationValidationException(
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
