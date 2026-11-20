package com.ferozfaiz.uniapi.category.application.provider;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryOrderingPort extends AdminCategoryOperationPort {
  AdminCategoryModels.Category reorder(Long id, AdminCategoryReorderCommand command);
}
