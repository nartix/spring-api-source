package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductCurrentPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueProductCurrentPriceJpaRepository
  extends JpaRepository<CatalogueProductCurrentPriceEntity, Long>
{
  boolean existsByProductId(Long productId);
}
