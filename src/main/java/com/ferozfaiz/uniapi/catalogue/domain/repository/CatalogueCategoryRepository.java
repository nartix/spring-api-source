package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.List;
import java.util.Optional;

public interface CatalogueCategoryRepository {
  default CatalogueCategory save(CatalogueCategory category) {
    return save(category, SparseOrderTarget.append());
  }

  CatalogueCategory save(CatalogueCategory category, SparseOrderTarget orderTarget);

  default PageResult<CatalogueCategory> findAll(PageCriteria page, SortCriteria sort) {
    return findAll(CategoryParentScope.all(), AdminCatalogueCategoryFilters.none(), page, sort);
  }

  default PageResult<CatalogueCategory> findAll(
    CategoryParentScope parentScope,
    PageCriteria page,
    SortCriteria sort
  ) {
    return findAll(parentScope, AdminCatalogueCategoryFilters.none(), page, sort);
  }

  PageResult<CatalogueCategory> findAll(
    CategoryParentScope parentScope,
    AdminCatalogueCategoryFilters filters,
    PageCriteria page,
    SortCriteria sort
  );

  default PageResult<CatalogueCategoryNode> findAllNodes(PageCriteria page, SortCriteria sort) {
    return findAllNodes(
      CategoryParentScope.all(),
      AdminCatalogueCategoryFilters.none(),
      page,
      sort
    );
  }

  default PageResult<CatalogueCategoryNode> findAllNodes(
    CategoryParentScope parentScope,
    PageCriteria page,
    SortCriteria sort
  ) {
    return findAllNodes(parentScope, AdminCatalogueCategoryFilters.none(), page, sort);
  }

  PageResult<CatalogueCategoryNode> findAllNodes(
    CategoryParentScope parentScope,
    AdminCatalogueCategoryFilters filters,
    PageCriteria page,
    SortCriteria sort
  );

  PageResult<CatalogueCategoryTreeNode> findTree(PageCriteria page, SortCriteria sort);

  long countTreeRows();

  Optional<CatalogueCategory> findById(Long id);

  Optional<CatalogueCategoryNode> findNodeById(Long id);

  List<CatalogueCategoryBreadcrumbItem> findBreadcrumbById(Long categoryId);

  CatalogueCategoryParentOptionSlice findParentOptions(CatalogueCategoryParentOptionLookup lookup);

  Optional<CatalogueCategory> findActiveBySlug(String slug);

  List<Long> findSelfAndDescendantIds(String slug);

  boolean existsSlug(String slug, Long excludingId);

  boolean hasChildren(Long categoryId);

  boolean hasAssignedProducts(Long categoryId);

  boolean isDescendantOf(Long descendantId, Long ancestorId);

  default void move(Long categoryId, Long newParentId) {
    move(categoryId, newParentId, SparseOrderTarget.append());
  }

  void move(Long categoryId, Long newParentId, SparseOrderTarget orderTarget);

  void deleteById(Long id);
}
