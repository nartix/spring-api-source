package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantProductOffer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantStore;

public interface CatalogueMerchantRepository {
  CatalogueMerchantStore save(CatalogueMerchantStore store);

  CatalogueMerchantProductOffer saveOffer(CatalogueMerchantProductOffer offer);

  boolean existsStoreSlug(String slug, Long excludingId);
}
