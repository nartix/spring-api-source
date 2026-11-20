package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository;

import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueMerchantProductOfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueMerchantProductOfferJpaRepository
  extends JpaRepository<CatalogueMerchantProductOfferEntity, Long>
{
  boolean existsByProductId(Long productId);
}
