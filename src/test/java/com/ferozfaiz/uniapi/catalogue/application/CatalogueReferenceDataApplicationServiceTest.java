package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CatalogueReferenceDataApplicationServiceTest {

  @Mock
  private CatalogueReferenceDataRepository referenceData;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Test
  void createsBrandAndManufacturerAndRejectsDuplicateSlugs() {
    CatalogueReferenceDataApplicationService service = new CatalogueReferenceDataApplicationService(
      referenceData,
      slugWriteLock
    );
    when(referenceData.saveBrand(argThat(brand -> "samsung".equals(brand.slug())))).thenReturn(
      new CatalogueBrand(1L, "Samsung", "samsung", true, null, null)
    );
    when(
      referenceData.saveManufacturer(
        argThat(manufacturer -> "samsung-electronics".equals(manufacturer.slug()))
      )
    ).thenReturn(
      new CatalogueManufacturer(2L, "Samsung Electronics", "samsung-electronics", true, null, null)
    );

    assertThat(
      service.createOrUpdateBrand(new UpsertBrandCommand(null, "Samsung", "samsung", true)).id()
    ).isEqualTo(1L);
    assertThat(
      service
        .createOrUpdateManufacturer(
          new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
        )
        .id()
    ).isEqualTo(2L);

    when(referenceData.existsBrandSlug("samsung", null)).thenReturn(true);
    assertThatThrownBy(() ->
      service.createOrUpdateBrand(new UpsertBrandCommand(null, "Samsung", "samsung", true))
    ).isInstanceOf(DuplicateCatalogueSlugException.class);
  }

  @Test
  void listsGetsAndDeletesReferenceDataWithDeleteBlockers() {
    CatalogueReferenceDataApplicationService service = new CatalogueReferenceDataApplicationService(
      referenceData,
      slugWriteLock
    );
    ListReferenceDataQuery query = new ListReferenceDataQuery(
      new PageCriteria(0, 20),
      SortCriteria.of("name", SortDirection.ASC),
      null,
      true
    );
    when(referenceData.findBrands(query)).thenReturn(
      new PageResult<>(
        List.of(new CatalogueBrand(1L, "Samsung", "samsung", true, null, null)),
        0,
        20,
        1,
        1,
        true,
        true,
        query.sort()
      )
    );
    when(referenceData.findBrandById(1L)).thenReturn(
      Optional.of(new CatalogueBrand(1L, "Samsung", "samsung", true, null, null))
    );
    when(referenceData.findManufacturerById(2L)).thenReturn(
      Optional.of(
        new CatalogueManufacturer(
          2L,
          "Samsung Electronics",
          "samsung-electronics",
          true,
          null,
          null
        )
      )
    );

    assertThat(service.listBrands(query).totalElements()).isEqualTo(1);
    assertThat(service.getBrand(1L).slug()).isEqualTo("samsung");

    service.deleteBrand(1L);
    verify(referenceData).deleteBrandById(1L);

    when(referenceData.manufacturerHasProducts(2L)).thenReturn(true);
    assertThatThrownBy(() -> service.deleteManufacturer(2L)).isInstanceOf(
      CatalogueConflictException.class
    );
  }

  @Test
  void missingReferenceDataReturnsNotFound() {
    CatalogueReferenceDataApplicationService service = new CatalogueReferenceDataApplicationService(
      referenceData,
      slugWriteLock
    );
    when(referenceData.findBrandById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBrand(404L)).isInstanceOf(
      CatalogueReferenceNotFoundException.class
    );
  }

  @Test
  void readMethodsDeclareCatalogueReadPolicyAuthorization() throws Exception {
    assertPreAuthorize(
      "listBrands",
      new Class<?>[] { ListReferenceDataQuery.class },
      "@catalogueReadAuthorizationPolicy.canReadBrand(authentication, null)"
    );
    assertPreAuthorize(
      "getBrand",
      new Class<?>[] { Long.class },
      "@catalogueReadAuthorizationPolicy.canReadBrand(authentication, #id)"
    );
    assertPreAuthorize(
      "listManufacturers",
      new Class<?>[] { ListReferenceDataQuery.class },
      "@catalogueReadAuthorizationPolicy.canReadManufacturer(authentication, null)"
    );
    assertPreAuthorize(
      "getManufacturer",
      new Class<?>[] { Long.class },
      "@catalogueReadAuthorizationPolicy.canReadManufacturer(authentication, #id)"
    );
  }

  private static void assertPreAuthorize(
    String methodName,
    Class<?>[] parameterTypes,
    String expectedExpression
  ) throws NoSuchMethodException {
    Method method = CatalogueReferenceDataApplicationService.class.getDeclaredMethod(
      methodName,
      parameterTypes
    );

    assertThat(method.getAnnotation(PreAuthorize.class))
      .extracting(PreAuthorize::value)
      .isEqualTo(expectedExpression);
  }
}
