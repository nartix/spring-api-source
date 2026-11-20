package com.ferozfaiz.uniapi.category.application.provider;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.List;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public non-sealed interface AdminCategoryHierarchyReadPort extends AdminCategoryOperationPort {
  PageResult<AdminCategoryModels.CategoryNode> tree(AdminCategoryTreeQuery query);
  List<AdminCategoryModels.BreadcrumbItem> breadcrumb(Long id);
  AdminCategoryModels.ParentOptionSlice parentOptions(AdminCategoryParentOptionsQuery query);
}
