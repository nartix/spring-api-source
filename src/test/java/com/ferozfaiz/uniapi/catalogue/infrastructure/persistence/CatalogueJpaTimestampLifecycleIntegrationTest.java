package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueJpaTimestampLifecycleIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueReferenceDataApplicationService referenceData;

  @Autowired
  private CatalogueCategoryApplicationService categories;

  @Autowired
  private ProductApplicationService products;

  @Test
  void jpaSavesPopulateCreateAndUpdateTimestampsForCatalogueEntities() {
    var brand = referenceData.createOrUpdateBrand(
      new UpsertBrandCommand(null, "Samsung", "samsung", true)
    );
    var manufacturer = referenceData.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
    );
    var category = categories.create(
      new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true)
    );
    var product = products.create(
      new UpsertProductCommand(
        null,
        "Samsung 990 Pro 2TB",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        brand.id(),
        manufacturer.id(),
        null,
        null,
        true
      )
    );

    assertThat(brand.createdAt()).isNotNull();
    assertThat(brand.updatedAt()).isNotNull();
    assertThat(manufacturer.createdAt()).isNotNull();
    assertThat(manufacturer.updatedAt()).isNotNull();
    assertThat(category.createdAt()).isNotNull();
    assertThat(category.updatedAt()).isNotNull();
    assertThat(product.createdAt()).isNotNull();
    assertThat(product.updatedAt()).isNotNull();

    var updatedBrand = referenceData.createOrUpdateBrand(
      new UpsertBrandCommand(brand.id(), "Samsung Updated", "samsung", true)
    );
    var updatedManufacturer = referenceData.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(
        manufacturer.id(),
        "Samsung Electronics Updated",
        "samsung-electronics",
        true
      )
    );
    var updatedCategory = categories.update(
      new UpsertCatalogueCategoryCommand(category.id(), null, "Storage Updated", "storage", true)
    );
    var updatedProduct = products.update(
      new UpsertProductCommand(
        product.id(),
        "Samsung 990 Pro 2TB Updated",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        brand.id(),
        manufacturer.id(),
        null,
        null,
        true
      )
    );

    assertThat(updatedBrand.updatedAt()).isAfterOrEqualTo(updatedBrand.createdAt());
    assertThat(updatedManufacturer.updatedAt()).isAfterOrEqualTo(updatedManufacturer.createdAt());
    assertThat(updatedCategory.updatedAt()).isAfterOrEqualTo(updatedCategory.createdAt());
    assertThat(updatedProduct.updatedAt()).isAfterOrEqualTo(updatedProduct.createdAt());
  }
}
