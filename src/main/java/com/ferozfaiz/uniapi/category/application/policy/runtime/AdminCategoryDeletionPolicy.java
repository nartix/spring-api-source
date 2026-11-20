package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryDeletionValidationInput;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryDeletionPolicy extends AdminCategoryRuntimePolicy {
  void validateDelete(AdminCategoryDeletionValidationInput input);
}
