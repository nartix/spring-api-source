package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryHierarchyValidationInput;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryHierarchyPolicy extends AdminCategoryRuntimePolicy {
  void validate(AdminCategoryHierarchyValidationInput input);
}
