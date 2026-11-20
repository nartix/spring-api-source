package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueProductCategoryMembershipIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private ProductApplicationService products;

  @Test
  void assignmentAndRemovalRemainStateLevelIdempotent() {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    Long productId = insertProduct(
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );
    Long categoryId = insertCategory("Storage", "storage", null);
    insertClosure(categoryId, categoryId, 0);

    products.assignCategory(productId, categoryId);
    products.assignCategory(productId, categoryId);

    assertThat(productCategoryCount(productId, categoryId)).isEqualTo(1L);

    products.removeCategory(productId, categoryId);
    products.removeCategory(productId, categoryId);

    assertThat(productCategoryCount(productId, categoryId)).isZero();
  }

  private long productCategoryCount(Long productId, Long categoryId) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_product_category WHERE product_id = ? AND category_id = ?",
      Long.class,
      productId,
      categoryId
    );
  }
}
