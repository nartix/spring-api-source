package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueMerchantStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueMerchantStoreJpaRepository
  extends JpaRepository<CatalogueMerchantStoreEntity, Long> {}
