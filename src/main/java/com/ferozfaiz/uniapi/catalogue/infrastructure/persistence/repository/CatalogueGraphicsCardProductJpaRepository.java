package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueGraphicsCardProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueGraphicsCardProductJpaRepository
  extends JpaRepository<CatalogueGraphicsCardProductEntity, Long> {}
