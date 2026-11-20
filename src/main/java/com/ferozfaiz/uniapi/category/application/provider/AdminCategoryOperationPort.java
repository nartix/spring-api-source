package com.ferozfaiz.uniapi.category.application.provider;

import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public sealed interface AdminCategoryOperationPort
  permits
    AdminCategoryReadPort,
    AdminCategoryHierarchyReadPort,
    AdminCategoryBasicMutationPort,
    AdminCategoryHierarchyMutationPort,
    AdminCategoryOrderingPort,
    AdminCategoryLifecyclePort {}
