package com.ferozfaiz.uniapi.category.application.policy.runtime;

import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public sealed interface AdminCategoryRuntimePolicy
  permits
    AdminCategoryGeneralValidationPolicy,
    AdminCategoryQueryPolicy,
    AdminCategoryHierarchyPolicy,
    AdminCategoryMutationValidationPolicy,
    AdminCategoryOrderingPolicy,
    AdminCategoryDeletionPolicy,
    AdminCategoryLifecyclePolicy {}
