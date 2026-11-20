package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueManufacturerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CatalogueManufacturerJpaRepository
  extends
    JpaRepository<CatalogueManufacturerEntity, Long>,
    JpaSpecificationExecutor<CatalogueManufacturerEntity>
{
  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);
}
