package com.ferozfaiz.uniapi.category.application.policy.runtime;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryOrderingPolicy extends AdminCategoryRuntimePolicy {
  SparseOrderTarget validatePlacement(AdminCategoryPlacementValidationInput input);
}
