package com.ferozfaiz.uniapi.category.application.provider;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryReadPort extends AdminCategoryOperationPort {
  PageResult<AdminCategoryModels.CategoryNode> list(AdminCategoryListQuery query);

  default AdminCategoryModels.CategoryNode getNode(Long id) {
    return new AdminCategoryModels.CategoryNode(get(id), 0, false);
  }

  AdminCategoryModels.Category get(Long id);
}
