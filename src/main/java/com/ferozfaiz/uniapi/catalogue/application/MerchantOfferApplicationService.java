package com.ferozfaiz.uniapi.catalogue.application;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertMerchantOfferCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertMerchantStoreCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantProductOffer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantStore;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueMerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MerchantOfferApplicationService {

  private final CatalogueMerchantRepository merchants;

  public MerchantOfferApplicationService(CatalogueMerchantRepository merchants) {
    this.merchants = merchants;
  }

  public CatalogueMerchantStore createOrUpdateStore(UpsertMerchantStoreCommand command) {
    if (merchants.existsStoreSlug(command.slug(), command.id())) {
      throw DuplicateCatalogueSlugException.merchantStore(command.slug());
    }
    return merchants.save(
      new CatalogueMerchantStore(
        command.id(),
        command.name(),
        command.slug(),
        command.active(),
        null,
        null
      )
    );
  }

  public CatalogueMerchantProductOffer createOrUpdateOffer(UpsertMerchantOfferCommand command) {
    return merchants.saveOffer(
      new CatalogueMerchantProductOffer(
        command.id(),
        command.productId(),
        command.merchantStoreId(),
        command.merchantSku(),
        command.merchantProductCode(),
        command.offerUrl(),
        command.active(),
        null,
        null
      )
    );
  }
}
