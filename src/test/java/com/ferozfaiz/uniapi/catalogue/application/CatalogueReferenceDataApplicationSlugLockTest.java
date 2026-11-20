package com.ferozfaiz.uniapi.catalogue.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogueReferenceDataApplicationSlugLockTest {

  @Mock
  private CatalogueReferenceDataRepository referenceData;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Test
  void locksBrandSlugBeforeDuplicateCheck() {
    when(referenceData.saveBrand(org.mockito.ArgumentMatchers.any())).thenReturn(
      new CatalogueBrand(1L, "Samsung", "samsung", true, null, null)
    );
    var service = new CatalogueReferenceDataApplicationService(referenceData, slugWriteLock);

    service.createOrUpdateBrand(new UpsertBrandCommand(null, "Samsung", "samsung", true));

    var inOrder = inOrder(slugWriteLock, referenceData);
    inOrder.verify(slugWriteLock).lock(ResourceType.BRAND, "samsung");
    inOrder.verify(referenceData).existsBrandSlug("samsung", null);
  }

  @Test
  void locksManufacturerSlugBeforeDuplicateCheck() {
    when(referenceData.saveManufacturer(org.mockito.ArgumentMatchers.any())).thenReturn(
      new CatalogueManufacturer(2L, "Samsung Electronics", "samsung-electronics", true, null, null)
    );
    var service = new CatalogueReferenceDataApplicationService(referenceData, slugWriteLock);

    service.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
    );

    var inOrder = inOrder(slugWriteLock, referenceData);
    inOrder.verify(slugWriteLock).lock(ResourceType.MANUFACTURER, "samsung-electronics");
    inOrder.verify(referenceData).existsManufacturerSlug("samsung-electronics", null);
  }
}
