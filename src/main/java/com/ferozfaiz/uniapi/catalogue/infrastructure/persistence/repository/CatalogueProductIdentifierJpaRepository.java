package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductIdentifierEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueProductIdentifierJpaRepository
  extends JpaRepository<CatalogueProductIdentifierEntity, Long>
{
  List<CatalogueProductIdentifierEntity> findByProductIdOrderByPrimaryDescIdAsc(Long productId);

  void deleteByProductId(Long productId);

  boolean existsByCanonicalGtin14(String canonicalGtin14);

  boolean existsByCanonicalGtin14AndProductIdNot(String canonicalGtin14, Long productId);
}
