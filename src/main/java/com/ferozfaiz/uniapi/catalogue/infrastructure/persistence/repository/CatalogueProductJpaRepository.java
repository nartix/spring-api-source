package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueProductJpaRepository extends JpaRepository<CatalogueProductEntity, Long> {
  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);

  Optional<CatalogueProductEntity> findByIdAndActiveTrue(Long id);

  boolean existsByBrandId(Long brandId);

  boolean existsByManufacturerId(Long manufacturerId);
}
