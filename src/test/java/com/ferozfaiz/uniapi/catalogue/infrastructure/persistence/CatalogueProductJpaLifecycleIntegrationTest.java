package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateProductIdentifierException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.domain.validation.ProductIdentifierRules;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueProductJpaLifecycleIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private ProductApplicationService products;

  @Autowired
  private CatalogueProductRepository productRepository;

  @Test
  void productLifecycleUsesJpaForCreateUpdateFindActiveSlugChecksAndDelete() {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");

    var created = products.create(productCommand(null, brandId, manufacturerId, true));

    assertThat(productRepository.findById(created.id()))
      .get()
      .extracting("title")
      .isEqualTo("Samsung 990 Pro 2TB");
    assertThat(productRepository.findActiveById(created.id())).isPresent();
    assertThat(productRepository.existsSlug("samsung-990-pro-2tb", null)).isTrue();
    assertThat(productRepository.existsSlug("samsung-990-pro-2tb", created.id())).isFalse();

    var updated = products.update(
      new UpsertProductCommand(
        created.id(),
        "Samsung 990 Pro 2TB Updated",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        brandId,
        manufacturerId,
        null,
        "Updated",
        true
      )
    );

    assertThat(updated.title()).isEqualTo("Samsung 990 Pro 2TB Updated");

    products.deactivate(created.id());
    assertThat(productRepository.findActiveById(created.id())).isEmpty();
    products.delete(created.id());
    assertThat(productRepository.findById(created.id())).isEmpty();
  }

  @Test
  void productDeleteGuardsRemainInForceForDependentRows() {
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
    Long storeId = insertMerchantStore("Amazon Canada", "amazon-ca");
    Long offerId = insertOffer(productId, storeId);
    insertCurrentPrice(offerId, productId, storeId);

    assertThat(productRepository.hasMerchantOffers(productId)).isTrue();
    assertThat(productRepository.hasCurrentPrices(productId)).isTrue();

    assertThatThrownBy(() -> products.delete(productId)).isInstanceOf(
      CatalogueConflictException.class
    );
  }

  @Test
  void productIdentifiersPersistUniquelyAndCascadeWithProducts() {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    var identifier = ProductIdentifierRules.normalize("UPC", "036000291452", true);

    var created = products.create(
      productCommand(null, brandId, manufacturerId, true, List.of(identifier))
    );

    assertThat(productRepository.findById(created.id()))
      .get()
      .extracting(product -> product.identifiers().getFirst().canonicalGtin14())
      .isEqualTo("00036000291452");
    assertThat(productRepository.existsIdentifier("00036000291452", null)).isTrue();
    assertThat(productRepository.existsIdentifier("00036000291452", created.id())).isFalse();
    assertThatThrownBy(() ->
      products.create(
        new UpsertProductCommand(
          null,
          "Samsung T7 Shield 1TB",
          "samsung-t7-shield-1tb",
          ProductFamily.STORAGE,
          brandId,
          manufacturerId,
          null,
          null,
          true,
          List.of(ProductIdentifierRules.normalize("GTIN-14", "00036000291452", true))
        )
      )
    ).isInstanceOf(DuplicateProductIdentifierException.class);

    products.delete(created.id());

    assertThat(countRows("catalogue_product_identifier")).isZero();
  }

  private static UpsertProductCommand productCommand(
    Long id,
    Long brandId,
    Long manufacturerId,
    boolean active
  ) {
    return productCommand(id, brandId, manufacturerId, active, List.of());
  }

  private static UpsertProductCommand productCommand(
    Long id,
    Long brandId,
    Long manufacturerId,
    boolean active,
    List<CatalogueProductIdentifier> identifiers
  ) {
    return new UpsertProductCommand(
      id,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      "https://example.test/990-pro.png",
      "Fast NVMe storage",
      active,
      identifiers
    );
  }
}
