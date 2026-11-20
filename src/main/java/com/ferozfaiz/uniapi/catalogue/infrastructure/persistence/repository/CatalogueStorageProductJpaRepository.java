package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueStorageProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueStorageProductJpaRepository
  extends JpaRepository<CatalogueStorageProductEntity, Long> {}
