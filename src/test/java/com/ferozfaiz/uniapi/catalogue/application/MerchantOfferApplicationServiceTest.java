package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertMerchantOfferCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertMerchantStoreCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantProductOffer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantStore;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueMerchantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantOfferApplicationServiceTest {

  @Mock
  private CatalogueMerchantRepository merchants;

  @Test
  void createsMerchantStoreAndOfferAndRejectsDuplicateStoreSlug() {
    MerchantOfferApplicationService service = new MerchantOfferApplicationService(merchants);
    when(merchants.save(argThat(store -> "amazon-ca".equals(store.slug())))).thenReturn(
      new CatalogueMerchantStore(1L, "Amazon Canada", "amazon-ca", true, null, null)
    );
    when(merchants.saveOffer(argThat(offer -> offer.productId().equals(10L)))).thenReturn(
      new CatalogueMerchantProductOffer(2L, 10L, 1L, "SKU-10", null, null, true, null, null)
    );

    assertThat(
      service
        .createOrUpdateStore(
          new UpsertMerchantStoreCommand(null, "Amazon Canada", "amazon-ca", true)
        )
        .id()
    ).isEqualTo(1L);
    assertThat(
      service
        .createOrUpdateOffer(
          new UpsertMerchantOfferCommand(null, 10L, 1L, "SKU-10", null, null, true)
        )
        .id()
    ).isEqualTo(2L);

    when(merchants.existsStoreSlug("amazon-ca", null)).thenReturn(true);
    assertThatThrownBy(() ->
      service.createOrUpdateStore(
        new UpsertMerchantStoreCommand(null, "Amazon Canada", "amazon-ca", true)
      )
    ).isInstanceOf(DuplicateCatalogueSlugException.class);
  }
}
