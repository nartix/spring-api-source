package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueReferenceDataJpaLifecycleIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueReferenceDataApplicationService referenceData;

  @Autowired
  private CatalogueReferenceDataRepository referenceRepository;

  @Test
  void brandAndManufacturerLifecycleUsesJpaForSaveFindSlugChecksIdsAndDelete() {
    var brand = referenceData.createOrUpdateBrand(
      new UpsertBrandCommand(null, "Samsung", "samsung", true)
    );
    var manufacturer = referenceData.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
    );

    assertThat(referenceRepository.findBrandById(brand.id())).isPresent();
    assertThat(referenceRepository.findManufacturerById(manufacturer.id())).isPresent();
    assertThat(referenceRepository.existsBrandId(brand.id())).isTrue();
    assertThat(referenceRepository.existsManufacturerId(manufacturer.id())).isTrue();
    assertThat(referenceRepository.existsBrandSlug("samsung", null)).isTrue();
    assertThat(referenceRepository.existsBrandSlug("samsung", brand.id())).isFalse();
    assertThat(referenceRepository.existsManufacturerSlug("samsung-electronics", null)).isTrue();
    assertThat(
      referenceRepository.existsManufacturerSlug("samsung-electronics", manufacturer.id())
    ).isFalse();

    assertThat(
      referenceData
        .createOrUpdateBrand(new UpsertBrandCommand(brand.id(), "Samsung Updated", "samsung", true))
        .name()
    ).isEqualTo("Samsung Updated");
    assertThatThrownBy(() ->
      referenceData.createOrUpdateBrand(
        new UpsertBrandCommand(null, "Samsung Duplicate", "samsung", true)
      )
    ).isInstanceOf(DuplicateCatalogueSlugException.class);

    referenceData.deleteBrand(brand.id());
    referenceData.deleteManufacturer(manufacturer.id());
    assertThat(referenceRepository.findBrandById(brand.id())).isEmpty();
    assertThat(referenceRepository.findManufacturerById(manufacturer.id())).isEmpty();
  }

  @Test
  void referencedBrandAndManufacturerDeletesRemainBlocked() {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    insertProduct(
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );

    assertThat(referenceRepository.brandHasProducts(brandId)).isTrue();
    assertThat(referenceRepository.manufacturerHasProducts(manufacturerId)).isTrue();
    assertThatThrownBy(() -> referenceData.deleteBrand(brandId)).isInstanceOf(
      CatalogueConflictException.class
    );
    assertThatThrownBy(() -> referenceData.deleteManufacturer(manufacturerId)).isInstanceOf(
      CatalogueConflictException.class
    );
  }
}
