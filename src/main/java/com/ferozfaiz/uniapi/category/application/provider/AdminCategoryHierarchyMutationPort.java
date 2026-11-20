package com.ferozfaiz.uniapi.category.application.provider;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryHierarchyMutationPort extends AdminCategoryOperationPort {
  void move(Long id, AdminCategoryMoveCommand command);
}
