package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryLifecycleValidationInput;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryLifecyclePolicy extends AdminCategoryRuntimePolicy {
  void validateLifecycle(AdminCategoryLifecycleValidationInput input);
}
