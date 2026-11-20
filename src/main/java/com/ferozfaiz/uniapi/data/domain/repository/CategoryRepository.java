package com.ferozfaiz.uniapi.data.domain.repository;

import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryDeleteEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.Nullable;

public interface CategoryRepository {
  Category save(Category category);

  default Category saveRoot(Category category) {
    return saveRoot(category, SparseOrderTarget.append());
  }

  Category saveRoot(Category category, SparseOrderTarget orderTarget);

  default Category saveChild(Category category, Long parentId) {
    return saveChild(category, parentId, SparseOrderTarget.append());
  }

  Category saveChild(Category category, Long parentId, SparseOrderTarget orderTarget);

  Optional<Category> findById(Long id);

  Optional<CategoryNode> findNodeById(Long id);

  List<Category> findRoots();

  List<Category> findChildren(Long parentId);

  List<Category> findAncestors(Long id);

  List<Category> findDescendants(Long id);

  List<Category> findAllForTree();

  List<Category> findSelfAndDescendants(Long id);

  PageResult<CategoryNode> findAll(PageCriteria page, SortCriteria sort);

  long count();

  long countSelfAndDescendants(Long id);

  CategoryMoveEstimate estimateMove(Long id, @Nullable Long parentId);

  CategoryDeleteEstimate estimateDelete(Long id);

  boolean existsByParentIdAndSlug(@Nullable Long parentId, String slug);

  boolean existsByParentIdAndSlugAndIdNot(@Nullable Long parentId, String slug, Long id);

  boolean hasChildren(Long id);

  default void move(
    Long id,
    @Nullable Long parentId,
    CategoryMoveMode mode,
    Duration statementTimeout
  ) {
    move(id, parentId, mode, statementTimeout, SparseOrderTarget.append());
  }

  void move(
    Long id,
    @Nullable Long parentId,
    CategoryMoveMode mode,
    Duration statementTimeout,
    SparseOrderTarget orderTarget
  );

  void deleteById(Long id);
}
