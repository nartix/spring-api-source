package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductCategoryEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueProductCategoryJpaRepository
  extends JpaRepository<CatalogueProductCategoryEntity, CatalogueProductCategoryId>
{
  boolean existsByProductIdAndCategoryId(Long productId, Long categoryId);

  boolean existsByProductId(Long productId);

  boolean existsByCategoryId(Long categoryId);

  void deleteByProductIdAndCategoryId(Long productId, Long categoryId);
}
