package com.ferozfaiz.uniapi.catalogue.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import com.ferozfaiz.uniapi.catalogue.application.validation.ProductCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductApplicationSlugLockTest {

  @Mock
  private CatalogueProductRepository products;

  @Mock
  private CatalogueReferenceDataRepository referenceData;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Test
  void locksProductSlugBeforeDuplicateCheck() {
    when(referenceData.existsBrandId(10L)).thenReturn(true);
    when(referenceData.existsManufacturerId(20L)).thenReturn(true);
    when(products.save(org.mockito.ArgumentMatchers.any())).thenReturn(product());
    var service = new ProductApplicationService(
      products,
      referenceData,
      new ProductCommandValidator(),
      slugWriteLock
    );

    service.create(
      new UpsertProductCommand(
        null,
        "Samsung 990 Pro 2TB",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        10L,
        20L,
        null,
        null,
        true
      )
    );

    var inOrder = inOrder(slugWriteLock, products);
    inOrder.verify(slugWriteLock).lock(ResourceType.PRODUCT, "samsung-990-pro-2tb");
    inOrder.verify(products).existsSlug("samsung-990-pro-2tb", null);
  }

  private static CatalogueProduct product() {
    return new CatalogueProduct(
      1L,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      new CatalogueBrand(10L, "Samsung", "samsung", true, null, null),
      new CatalogueManufacturer(
        20L,
        "Samsung Electronics",
        "samsung-electronics",
        true,
        null,
        null
      ),
      null,
      null,
      true,
      null,
      null
    );
  }
}
