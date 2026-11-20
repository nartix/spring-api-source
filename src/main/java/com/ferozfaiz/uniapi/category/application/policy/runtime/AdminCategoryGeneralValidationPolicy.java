package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryGeneralValidationInput;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryGeneralValidationPolicy
  extends AdminCategoryRuntimePolicy
{
  void validate(AdminCategoryGeneralValidationInput input);
}
