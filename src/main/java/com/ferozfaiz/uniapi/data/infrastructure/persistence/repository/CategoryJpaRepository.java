package com.ferozfaiz.uniapi.data.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.data.infrastructure.persistence.entity.CategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
  List<CategoryEntity> findAllByOrderByParentIdAscSortRankAscIdAsc();

  List<CategoryEntity> findByParentIdIsNullOrderBySortRankAscIdAsc();

  List<CategoryEntity> findByParentIdOrderBySortRankAscIdAsc(Long parentId);

  boolean existsByParentId(Long parentId);

  boolean existsByParentIdAndSlug(Long parentId, String slug);

  boolean existsByParentIdIsNullAndSlug(String slug);

  boolean existsByParentIdAndSlugAndIdNot(Long parentId, String slug, Long id);

  boolean existsByParentIdIsNullAndSlugAndIdNot(String slug, Long id);
}
