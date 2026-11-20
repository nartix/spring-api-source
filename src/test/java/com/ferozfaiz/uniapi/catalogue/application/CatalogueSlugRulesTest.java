package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.validation.ProductCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogueSlugRulesTest {

  @Mock
  private CatalogueProductRepository products;

  @Mock
  private CatalogueReferenceDataRepository referenceData;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Test
  void duplicateProductSlugAndDuplicateReferenceSlugAreRejected() {
    ProductApplicationService productService = new ProductApplicationService(
      products,
      referenceData,
      new ProductCommandValidator(),
      slugWriteLock
    );
    UpsertProductCommand first = productCommand("samsung-990-pro-2tb");
    when(products.existsSlug("samsung-990-pro-2tb", first.id())).thenReturn(true);

    assertThatThrownBy(() -> productService.create(first)).isInstanceOf(
      DuplicateCatalogueSlugException.class
    );

    when(referenceData.existsBrandSlug("samsung", null)).thenReturn(true);
    CatalogueReferenceDataApplicationService referenceService =
      new CatalogueReferenceDataApplicationService(referenceData, slugWriteLock);
    assertThatThrownBy(() ->
      referenceService.createOrUpdateBrand(new UpsertBrandCommand(null, "Samsung", "samsung", true))
    ).isInstanceOf(DuplicateCatalogueSlugException.class);
  }

  private static UpsertProductCommand productCommand(String slug) {
    return new UpsertProductCommand(
      null,
      "Samsung 990 Pro 2TB",
      slug,
      ProductFamily.STORAGE,
      10L,
      20L,
      null,
      null,
      true
    );
  }
}
