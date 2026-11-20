package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCategoryEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogueCategoryJpaRepository
  extends
    JpaRepository<CatalogueCategoryEntity, Long>,
    JpaSpecificationExecutor<CatalogueCategoryEntity>
{
  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);

  boolean existsByParentId(Long parentId);

  Page<CatalogueCategoryEntity> findByParentId(Long parentId, Pageable pageable);

  @Query(
    """
    select category.parentId
    from CatalogueCategoryEntity category
    where category.parentId in :parentIds
    group by category.parentId
    """
  )
  List<Long> findParentIdsWithChildren(@Param("parentIds") Collection<Long> parentIds);

  Optional<CatalogueCategoryEntity> findBySlugAndActiveTrue(String slug);
}
