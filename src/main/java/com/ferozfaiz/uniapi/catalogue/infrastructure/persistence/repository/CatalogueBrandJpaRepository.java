package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueBrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CatalogueBrandJpaRepository
  extends JpaRepository<CatalogueBrandEntity, Long>, JpaSpecificationExecutor<CatalogueBrandEntity>
{
  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);
}
