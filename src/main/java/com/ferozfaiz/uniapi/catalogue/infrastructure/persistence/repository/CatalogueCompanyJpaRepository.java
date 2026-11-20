package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueCompanyJpaRepository
  extends JpaRepository<CatalogueCompanyEntity, Long> {}
