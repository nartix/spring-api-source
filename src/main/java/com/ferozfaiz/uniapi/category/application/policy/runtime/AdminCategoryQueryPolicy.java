package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryQueryPolicy extends AdminCategoryRuntimePolicy {
  AdminCategoryListQuery validateList(AdminCategoryListValidationInput input);
  AdminCategoryTreeQuery validateTree(AdminCategoryTreeValidationInput input);
  AdminCategoryParentOptionsQuery validateParentOptions(
    AdminCategoryParentOptionsValidationInput input
  );
}
