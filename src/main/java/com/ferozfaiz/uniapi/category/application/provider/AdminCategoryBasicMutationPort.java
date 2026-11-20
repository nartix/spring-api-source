package com.ferozfaiz.uniapi.category.application.provider;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryBasicMutationPort extends AdminCategoryOperationPort {
  AdminCategoryModels.Category create(AdminCategoryCreateCommand command);
  AdminCategoryModels.Category replace(Long id, AdminCategoryUpdateCommand command);
  AdminCategoryModels.Category patch(Long id, AdminCategoryPatchCommand command);
  void delete(Long id);
}
