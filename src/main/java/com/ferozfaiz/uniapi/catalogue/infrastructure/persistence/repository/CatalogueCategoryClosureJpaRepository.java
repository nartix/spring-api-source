package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCategoryClosureEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCategoryClosureId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueCategoryClosureJpaRepository
  extends JpaRepository<CatalogueCategoryClosureEntity, CatalogueCategoryClosureId>
{
  List<CatalogueCategoryClosureEntity> findByDescendantId(Long descendantId);

  List<CatalogueCategoryClosureEntity> findByDescendantIdIn(Collection<Long> descendantIds);

  boolean existsByAncestorIdAndDepthGreaterThan(Long ancestorId, int depth);

  boolean existsByAncestorIdAndDescendantIdAndDepthGreaterThan(
    Long ancestorId,
    Long descendantId,
    int depth
  );
}
