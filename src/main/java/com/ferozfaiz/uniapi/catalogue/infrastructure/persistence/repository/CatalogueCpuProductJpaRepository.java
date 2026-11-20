package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCpuProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueCpuProductJpaRepository
  extends JpaRepository<CatalogueCpuProductEntity, Long> {}
