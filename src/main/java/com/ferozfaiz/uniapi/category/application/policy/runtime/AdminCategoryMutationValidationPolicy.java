package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryMutationValidationPolicy
  extends AdminCategoryRuntimePolicy
{
  AdminCategoryCreateCommand validateCreate(AdminCategoryCreateValidationInput input);
  AdminCategoryUpdateCommand validateReplace(AdminCategoryReplaceValidationInput input);
  AdminCategoryPatchCommand validatePatch(AdminCategoryPatchValidationInput input);
}
