package com.ferozfaiz.uniapi.category.application.provider;

import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryLifecyclePort extends AdminCategoryOperationPort {
  void activate(Long id);
  void deactivate(Long id);
}
