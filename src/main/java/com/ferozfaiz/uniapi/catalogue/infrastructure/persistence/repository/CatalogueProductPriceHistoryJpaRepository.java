package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductPriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueProductPriceHistoryJpaRepository
  extends JpaRepository<CatalogueProductPriceHistoryEntity, Long>
{
  boolean existsByProductId(Long productId);
}
