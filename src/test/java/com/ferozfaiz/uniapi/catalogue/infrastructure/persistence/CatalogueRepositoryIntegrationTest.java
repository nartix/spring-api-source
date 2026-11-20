package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.MerchantOfferApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertMerchantOfferCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertMerchantStoreCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.GetProductDetailQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Availability;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Currency;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Marketplace;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.PriceSource;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CataloguePrice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CataloguePriceRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
  properties = {
    "app.security.oauth2.clients.nextjs-server.scopes[0]=api",
    "app.security.oauth2.clients.nextjs-server.scopes[1]=catalogue:read",
    "app.security.oauth2.clients.nextjs-server.scopes[2]=catalogue:write",
  }
)
@Transactional
class CatalogueRepositoryIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T10:15:30Z");

  @Autowired
  private CatalogueProductListingRepository products;

  @Autowired
  private CatalogueProductRepository productWrites;

  @Autowired
  private CataloguePriceRepository prices;

  @Autowired
  private CatalogueCategoryRepository categories;

  @Autowired
  private CatalogueReferenceDataRepository referenceDataRepository;

  @Autowired
  private CatalogueReferenceDataApplicationService referenceDataService;

  @Autowired
  private MerchantOfferApplicationService merchantOfferService;

  @Autowired
  private ProductApplicationService productService;

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void cleanCatalogueTables() {
    jdbc.execute(
      """
      TRUNCATE TABLE
        catalogue_product_price_history,
        catalogue_product_current_price,
        catalogue_merchant_product_offer,
        catalogue_merchant_store,
        catalogue_product_category,
        catalogue_category_closure,
        catalogue_category,
        catalogue_product_identifier,
        catalogue_storage_product,
        catalogue_cpu_product,
        catalogue_graphics_card_product,
        catalogue_product,
        catalogue_brand,
        catalogue_manufacturer,
        catalogue_company
      RESTART IDENTITY CASCADE
      """
    );
  }

  @Test
  void listingQueriesFilterSortAndKeepProductsWithoutPricesLast() {
    CatalogueIds ids = seedCatalogue();

    var page = products.findActiveProducts(
      new ListProductsQuery(
        new PageCriteria(0, 10),
        new SortCriteria(
          List.of(
            new SortOrder("currentPrice", SortDirection.ASC),
            new SortOrder("id", SortDirection.ASC)
          )
        ),
        null,
        ProductFamily.STORAGE,
        null,
        null,
        null,
        null,
        "CA",
        null,
        Map.of("capacityGbMin", 1000)
      )
    );

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
      .extracting("id")
      .containsExactly(ids.storageProductId(), ids.unpricedStorageProductId());
    assertThat(page.content().getFirst().primaryIdentifier().canonicalGtin14()).isEqualTo(
      "00036000291452"
    );
    assertThat(page.content().get(0).priceAmount()).isEqualByComparingTo("149.9900");
    assertThat(page.content().get(1).priceAmount()).isNull();
  }

  @Test
  void categoryScopedListingIncludesDescendantProductsOnlyForActiveProducts() {
    CatalogueIds ids = seedCatalogue();

    var page = products.findActiveProducts(
      new ListProductsQuery(
        new PageCriteria(0, 10),
        SortCriteria.of("title", SortDirection.ASC),
        null,
        null,
        "storage",
        null,
        null,
        null,
        "CA",
        null,
        Map.of()
      )
    );

    assertThat(page.content())
      .extracting("id")
      .containsExactly(ids.storageProductId(), ids.unpricedStorageProductId());
  }

  @Test
  void familySpecificListingFiltersCoverStorageCpuAndGraphicsProducts() {
    CatalogueIds ids = seedCatalogue();

    var cpuPage = products.findActiveProducts(
      new ListProductsQuery(
        new PageCriteria(0, 10),
        SortCriteria.of("title", SortDirection.ASC),
        null,
        ProductFamily.CPU,
        null,
        null,
        null,
        null,
        "CA",
        null,
        Map.of("socket", "AM5", "coreCountMin", 8, "architecture", "Zen 4")
      )
    );
    var gpuPage = products.findActiveProducts(
      new ListProductsQuery(
        new PageCriteria(0, 10),
        SortCriteria.of("vramGb", SortDirection.DESC),
        null,
        ProductFamily.GPU,
        null,
        null,
        null,
        null,
        "CA",
        null,
        Map.of("gpuChipset", "RTX 4070 SUPER", "vramGbMin", 12, "lengthMmMax", 250)
      )
    );

    assertThat(cpuPage.content()).extracting("id").containsExactly(ids.cpuProductId());
    assertThat(cpuPage.content().getFirst().coreCount()).isEqualTo(8);
    assertThat(gpuPage.content()).extracting("id").containsExactly(ids.gpuProductId());
    assertThat(gpuPage.content().getFirst().vramGb()).isEqualTo(12);
  }

  @Test
  void detailQueryReturnsCtiSpecificFieldsCategoriesAndRejectsWrongSlug() {
    CatalogueIds ids = seedCatalogue();

    var detail = products
      .findActiveProductDetail(
        new GetProductDetailQuery(ids.storageProductId(), "samsung-990-pro-2tb", "CA", null)
      )
      .orElseThrow();

    assertThat(detail.product().storageType()).isEqualTo("NVME_SSD");
    assertThat(detail.product().capacityGb()).isEqualTo(2000);
    assertThat(detail.identifiers())
      .extracting("canonicalGtin14")
      .containsExactly("00036000291452");
    assertThat(detail.storageRpm()).isZero();
    assertThat(detail.storageCacheMb()).isEqualTo(2048);
    assertThat(detail.categories()).extracting("slug").containsExactly("nvme-ssds");
    assertThat(detail.product().priceAmount()).isEqualByComparingTo("149.9900");

    var cpuDetail = products
      .findActiveProductDetail(
        new GetProductDetailQuery(ids.cpuProductId(), "ryzen-7-7800x3d", "CA", null)
      )
      .orElseThrow();
    var gpuDetail = products
      .findActiveProductDetail(
        new GetProductDetailQuery(ids.gpuProductId(), "geforce-rtx-4070-super", "CA", null)
      )
      .orElseThrow();

    assertThat(cpuDetail.product().socket()).isEqualTo("AM5");
    assertThat(cpuDetail.baseClockGhz()).isEqualByComparingTo("4.20");
    assertThat(cpuDetail.cpuArchitecture()).isEqualTo("Zen 4");
    assertThat(cpuDetail.integratedGraphics()).isFalse();
    assertThat(cpuDetail.cpuTdpWatts()).isEqualTo(120);
    assertThat(gpuDetail.product().gpuChipset()).isEqualTo("RTX 4070 SUPER");
    assertThat(gpuDetail.product().vramGb()).isEqualTo(12);
    assertThat(gpuDetail.gpuInterfaceType()).isEqualTo("PCIe 4.0");
    assertThat(gpuDetail.gpuLengthMm()).isEqualTo(242);
    assertThat(gpuDetail.gpuTdpWatts()).isEqualTo(220);

    assertThat(
      products.findActiveProductDetail(
        new GetProductDetailQuery(ids.storageProductId(), "wrong-slug", "CA", null)
      )
    ).isEmpty();
  }

  @Test
  void priceRecordingUpsertsCurrentPriceAndDeduplicatesUnchangedHistory() {
    CatalogueIds ids = seedCatalogueWithoutPrices();

    CataloguePrice first = prices.recordCurrentPrice(price(ids.offerId(), "149.99", NOW));
    CataloguePrice unchanged = prices.recordCurrentPrice(
      price(ids.offerId(), "149.99", NOW.plusHours(1))
    );
    CataloguePrice changed = prices.recordCurrentPrice(
      price(ids.offerId(), "139.99", NOW.plusHours(2))
    );

    assertThat(first.productId()).isEqualTo(ids.storageProductId());
    assertThat(unchanged.id()).isEqualTo(first.id());
    assertThat(changed.id()).isEqualTo(first.id());
    assertThat(changed.amount()).isEqualByComparingTo("139.99");
    assertThat(historyCount(ids.offerId())).isEqualTo(2);
  }

  @Test
  void categoryRepositoryMaintainsClosureRowsForCreateAndMoveUseCases() {
    var root = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true)
    );
    var child = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, root.id(), "NVMe SSDs", "nvme-ssds", true)
    );

    assertThat(categories.findSelfAndDescendantIds("storage")).containsExactly(
      root.id(),
      child.id()
    );

    categoryService.move(child.id(), null);

    assertThat(categories.findSelfAndDescendantIds("storage")).containsExactly(root.id());
    assertThat(categories.findSelfAndDescendantIds("nvme-ssds")).containsExactly(child.id());
  }

  @Test
  void internalMaintenanceUseCasesPersistReferenceProductCategoryMerchantAndOfferData() {
    var brand = referenceDataService.createOrUpdateBrand(
      new UpsertBrandCommand(null, "Samsung", "samsung", true)
    );
    var manufacturer = referenceDataService.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
    );
    var category = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true)
    );
    var store = merchantOfferService.createOrUpdateStore(
      new UpsertMerchantStoreCommand(null, "Amazon Canada", "amazon-ca", true)
    );

    var product = productService.create(
      new UpsertProductCommand(
        null,
        "Samsung 990 Pro 2TB",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        brand.id(),
        manufacturer.id(),
        "https://example.test/990-pro.png",
        "Fast NVMe storage",
        false
      )
    );
    assertThatThrownBy(() ->
      productService.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB Heatsink",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          brand.id(),
          manufacturer.id(),
          null,
          null,
          true
        )
      )
    ).isInstanceOf(DuplicateCatalogueSlugException.class);
    productService.activate(product.id());
    productService.assignCategory(product.id(), category.id());
    var offer = merchantOfferService.createOrUpdateOffer(
      new UpsertMerchantOfferCommand(
        null,
        product.id(),
        store.id(),
        "SKU-" + product.id(),
        "MPN-" + product.id(),
        "https://example.test/offers/" + product.id(),
        true
      )
    );

    assertThat(product.id()).isPositive();
    assertThat(offer.id()).isPositive();
    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM catalogue_product_category WHERE product_id = ? AND category_id = ?",
        Long.class,
        product.id(),
        category.id()
      )
    ).isEqualTo(1L);
  }

  @Test
  void referenceDataRepositoryListsSearchesAndBlocksDeletesWhenProductsReferenceRows() {
    var samsung = referenceDataService.createOrUpdateBrand(
      new UpsertBrandCommand(null, "Samsung", "samsung", true)
    );
    referenceDataService.createOrUpdateBrand(
      new UpsertBrandCommand(null, "Crucial", "crucial", false)
    );
    var manufacturer = referenceDataService.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
    );

    var query = new ListReferenceDataQuery(
      new PageCriteria(0, 10),
      new SortCriteria(
        List.of(new SortOrder("name", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
      ),
      "sam",
      true
    );

    assertThat(referenceDataRepository.findBrands(query).content())
      .extracting(CatalogueBrand::slug)
      .containsExactly("samsung");
    assertThat(referenceDataRepository.findManufacturerById(manufacturer.id()))
      .get()
      .extracting(CatalogueManufacturer::slug)
      .isEqualTo("samsung-electronics");

    var product = productService.create(
      new UpsertProductCommand(
        null,
        "Samsung 990 Pro 2TB",
        "samsung-990-pro-2tb",
        ProductFamily.STORAGE,
        samsung.id(),
        manufacturer.id(),
        null,
        null,
        true
      )
    );

    assertThat(product.id()).isPositive();
    assertThatThrownBy(() -> referenceDataService.deleteBrand(samsung.id())).isInstanceOf(
      CatalogueConflictException.class
    );
    assertThatThrownBy(() ->
      referenceDataService.deleteManufacturer(manufacturer.id())
    ).isInstanceOf(CatalogueConflictException.class);
  }

  @Test
  void listingPriceSelectionSupportsMarketplaceAndMerchantSpecificCurrentPrices() {
    CatalogueIds ids = seedCatalogueWithoutPrices();
    Long bestBuyId = insertMerchantStore("Best Buy Canada", "best-buy-ca");
    Long bestBuyOfferId = insertOffer(ids.storageProductId(), bestBuyId);
    insertCurrentPrice(
      ids.offerId(),
      ids.storageProductId(),
      ids.merchantStoreId(),
      "149.99",
      "CA"
    );
    insertCurrentPrice(bestBuyOfferId, ids.storageProductId(), bestBuyId, "129.99", "CA");
    insertCurrentPrice(bestBuyOfferId, ids.storageProductId(), bestBuyId, "109.99", "US");

    var bestEligible = products.findActiveProducts(listingQuery(null, "CA"));
    var merchantSpecific = products.findActiveProducts(listingQuery("amazon-ca", "CA"));
    var marketplaceSpecific = products.findActiveProducts(listingQuery("best-buy-ca", "US"));

    assertThat(bestEligible.content().getFirst().merchantStoreSlug()).isEqualTo("best-buy-ca");
    assertThat(bestEligible.content().getFirst().priceAmount()).isEqualByComparingTo("129.9900");
    assertThat(merchantSpecific.content().getFirst().merchantStoreSlug()).isEqualTo("amazon-ca");
    assertThat(merchantSpecific.content().getFirst().priceAmount()).isEqualByComparingTo(
      "149.9900"
    );
    assertThat(marketplaceSpecific.content().getFirst().priceMarketplace()).isEqualTo("US");
    assertThat(marketplaceSpecific.content().getFirst().priceAmount()).isEqualByComparingTo(
      "109.9900"
    );
  }

  @Test
  void productWriteRepositoryRejectsSlugCollisionsAndDeleteBlockers() {
    CatalogueIds ids = seedCatalogueWithoutPrices();

    assertThat(productWrites.existsSlug("samsung-990-pro-2tb", null)).isTrue();
    assertThat(productWrites.existsSlug("samsung-990-pro-2tb", ids.storageProductId())).isFalse();
    assertThatThrownBy(() -> productService.delete(ids.storageProductId())).isInstanceOf(
      CatalogueConflictException.class
    );
  }

  @Test
  void productServiceRejectsMissingReferenceIdsBeforePersistence() {
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");

    assertThatThrownBy(() ->
      productService.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          999L,
          manufacturerId,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product brand was not found");
    assertThat(productCount()).isZero();

    Long brandId = insertBrand("Samsung", "samsung");

    assertThatThrownBy(() ->
      productService.create(
        new UpsertProductCommand(
          null,
          "Samsung 990 Pro 2TB",
          "samsung-990-pro-2tb",
          ProductFamily.STORAGE,
          brandId,
          999L,
          null,
          null,
          true
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product manufacturer was not found");
    assertThat(productCount()).isZero();
  }

  @Test
  void productRepositoryTranslatesBrandForeignKeyFailures() {
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");

    assertThatThrownBy(() ->
      productWrites.save(
        product(
          "Missing Brand Product",
          "missing-brand-product",
          ProductFamily.STORAGE,
          999L,
          manufacturerId
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product brand was not found");
  }

  @Test
  void productRepositoryTranslatesManufacturerForeignKeyFailures() {
    Long brandId = insertBrand("Samsung", "samsung");

    assertThatThrownBy(() ->
      productWrites.save(
        product(
          "Missing Manufacturer Product",
          "missing-manufacturer-product",
          ProductFamily.STORAGE,
          brandId,
          999L
        )
      )
    )
      .isInstanceOf(CatalogueReferenceNotFoundException.class)
      .hasMessage("product manufacturer was not found");
  }

  @Test
  void productDeleteAndCategoryMembershipAreStateLevelIdempotentWhenSafe() {
    Long brandId = insertBrand("Crucial", "crucial");
    Long manufacturerId = insertManufacturer("Micron", "micron");
    Long productId = insertProduct(
      "Crucial P3 1TB",
      "crucial-p3-1tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );
    Long categoryId = insertCategory("Storage", "storage", null);
    insertClosure(categoryId, categoryId, 0);

    productService.assignCategory(productId, categoryId);
    productService.assignCategory(productId, categoryId);
    assertThat(productCategoryCount(productId, categoryId)).isEqualTo(1L);

    productService.removeCategory(productId, categoryId);
    productService.removeCategory(productId, categoryId);
    assertThat(productCategoryCount(productId, categoryId)).isZero();

    productService.delete(productId);

    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM catalogue_product WHERE id = ?",
        Long.class,
        productId
      )
    ).isZero();
  }

  @Test
  void categoryDeleteAndMoveGuardrailsUseBoundedRepositoryChecks() {
    CatalogueIds ids = seedCatalogueWithoutPrices();

    assertThat(categories.hasChildren(ids.storageCategoryId())).isTrue();
    assertThat(categories.hasAssignedProducts(ids.storageCategoryId())).isTrue();
    assertThatThrownBy(() -> categoryService.delete(ids.storageCategoryId())).isInstanceOf(
      CatalogueConflictException.class
    );
    assertThatThrownBy(() ->
      categoryService.move(ids.storageCategoryId(), ids.nvmeCategoryId())
    ).isInstanceOf(CatalogueConflictException.class);

    Long root = insertCategory("Cooling", "cooling", null);
    insertClosure(root, root, 0);
    Long child = insertCategory("Air Cooling", "air-cooling", root);
    insertClosure(child, child, 0);
    insertClosure(root, child, 1);

    categoryService.move(child, null);
    assertThat(categories.findSelfAndDescendantIds("cooling")).containsExactly(root);

    categoryService.delete(child);
    assertThat(
      jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_category WHERE id = ?", Long.class, child)
    ).isZero();
  }

  private CatalogueIds seedCatalogue() {
    CatalogueIds ids = seedCatalogueWithoutPrices();
    insertCurrentPrice(
      ids.offerId(),
      ids.storageProductId(),
      ids.merchantStoreId(),
      "149.99",
      "CA"
    );
    return ids;
  }

  private CatalogueIds seedCatalogueWithoutPrices() {
    Long brandId = insertBrand("Samsung", "samsung");
    Long manufacturerId = insertManufacturer("Samsung Electronics", "samsung-electronics");
    Long merchantStoreId = insertMerchantStore("Amazon Canada", "amazon-ca");
    Long storage = insertProduct(
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );
    Long unpriced = insertProduct(
      "Samsung T7 Shield 1TB",
      "samsung-t7-shield-1tb",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      true
    );
    Long inactive = insertProduct(
      "Samsung Archived SSD",
      "samsung-archived-ssd",
      ProductFamily.STORAGE,
      brandId,
      manufacturerId,
      false
    );
    Long cpu = insertProduct(
      "Ryzen 7 7800X3D",
      "ryzen-7-7800x3d",
      ProductFamily.CPU,
      brandId,
      manufacturerId,
      true
    );
    Long gpu = insertProduct(
      "GeForce RTX 4070 Super",
      "geforce-rtx-4070-super",
      ProductFamily.GPU,
      brandId,
      manufacturerId,
      true
    );
    insertStorage(storage, "NVME_SSD", 2000, "NVME", "M_2_2280", 7450, 6900, 0, 2048);
    insertProductIdentifier(storage, "UPC", "036000291452", "00036000291452", true);
    insertStorage(unpriced, "PORTABLE_SSD", 1000, "USB_C", "2_5_INCH", 1050, 1000, 0, 0);
    insertStorage(inactive, "SATA_SSD", 500, "SATA", "2_5_INCH", 550, 520, 0, 512);
    insertCpu(cpu);
    insertGpu(gpu);
    Long storageCategory = insertCategory("Storage", "storage", null);
    Long nvmeCategory = insertCategory("NVMe SSDs", "nvme-ssds", storageCategory);
    Long componentsCategory = insertCategory("Components", "components", null);
    Long cpuCategory = insertCategory("CPUs", "cpus", componentsCategory);
    Long gpuCategory = insertCategory("Graphics Cards", "graphics-cards", componentsCategory);
    insertClosure(storageCategory, storageCategory, 0);
    insertClosure(nvmeCategory, nvmeCategory, 0);
    insertClosure(storageCategory, nvmeCategory, 1);
    insertClosure(componentsCategory, componentsCategory, 0);
    insertClosure(cpuCategory, cpuCategory, 0);
    insertClosure(gpuCategory, gpuCategory, 0);
    insertClosure(componentsCategory, cpuCategory, 1);
    insertClosure(componentsCategory, gpuCategory, 1);
    insertProductCategory(storage, nvmeCategory);
    insertProductCategory(unpriced, storageCategory);
    insertProductCategory(inactive, storageCategory);
    insertProductCategory(cpu, cpuCategory);
    insertProductCategory(gpu, gpuCategory);
    Long offerId = insertOffer(storage, merchantStoreId);
    return new CatalogueIds(
      storage,
      unpriced,
      cpu,
      gpu,
      merchantStoreId,
      offerId,
      storageCategory,
      nvmeCategory
    );
  }

  private static ListProductsQuery listingQuery(String merchantStore, String marketplace) {
    return new ListProductsQuery(
      new PageCriteria(0, 10),
      new SortCriteria(
        List.of(
          new SortOrder("currentPrice", SortDirection.ASC),
          new SortOrder("id", SortDirection.ASC)
        )
      ),
      null,
      ProductFamily.STORAGE,
      null,
      null,
      null,
      merchantStore,
      marketplace,
      true,
      Map.of()
    );
  }

  private Long insertBrand(String name, String slug) {
    return jdbc.queryForObject(
      "INSERT INTO catalogue_brand (name, slug) VALUES (?, ?) RETURNING id",
      Long.class,
      name,
      slug
    );
  }

  private Long insertManufacturer(String name, String slug) {
    return jdbc.queryForObject(
      "INSERT INTO catalogue_manufacturer (name, slug) VALUES (?, ?) RETURNING id",
      Long.class,
      name,
      slug
    );
  }

  private Long insertMerchantStore(String name, String slug) {
    return jdbc.queryForObject(
      "INSERT INTO catalogue_merchant_store (name, slug) VALUES (?, ?) RETURNING id",
      Long.class,
      name,
      slug
    );
  }

  private Long insertProduct(
    String title,
    String slug,
    ProductFamily family,
    Long brandId,
    Long manufacturerId,
    boolean active
  ) {
    return jdbc.queryForObject(
      """
      INSERT INTO catalogue_product (
        title, slug, product_family, brand_id, manufacturer_id, main_image_url,
        short_description, is_active, created_at, updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
      """,
      Long.class,
      title,
      slug,
      family.name(),
      brandId,
      manufacturerId,
      "https://example.test/" + slug + ".png",
      "Test product",
      active,
      NOW,
      NOW
    );
  }

  private void insertStorage(
    Long productId,
    String storageType,
    int capacityGb,
    String interfaceType,
    String formFactor,
    int readSpeedMbps,
    int writeSpeedMbps,
    int rpm,
    int cacheMb
  ) {
    jdbc.update(
      """
      INSERT INTO catalogue_storage_product (
        product_id, storage_type, capacity_gb, interface_type, form_factor,
        read_speed_mbps, write_speed_mbps, rpm, cache_mb
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """,
      productId,
      storageType,
      capacityGb,
      interfaceType,
      formFactor,
      readSpeedMbps,
      writeSpeedMbps,
      rpm,
      cacheMb
    );
  }

  private void insertProductIdentifier(
    Long productId,
    String family,
    String value,
    String canonicalGtin14,
    boolean primary
  ) {
    jdbc.update(
      """
      INSERT INTO catalogue_product_identifier (
        product_id, family, value, canonical_gtin14, is_primary
      )
      VALUES (?, ?, ?, ?, ?)
      """,
      productId,
      family,
      value,
      canonicalGtin14,
      primary
    );
  }

  private void insertCpu(Long productId) {
    jdbc.update(
      """
      INSERT INTO catalogue_cpu_product (
        product_id, socket, core_count, thread_count, base_clock_ghz,
        boost_clock_ghz, architecture, integrated_graphics, tdp_watts
      )
      VALUES (?, 'AM5', 8, 16, 4.20, 5.00, 'Zen 4', false, 120)
      """,
      productId
    );
  }

  private void insertGpu(Long productId) {
    jdbc.update(
      """
      INSERT INTO catalogue_graphics_card_product (
        product_id, gpu_chipset, vram_gb, memory_type, boost_clock_mhz,
        interface_type, length_mm, tdp_watts
      )
      VALUES (?, 'RTX 4070 SUPER', 12, 'GDDR6X', 2475, 'PCIe 4.0', 242, 220)
      """,
      productId
    );
  }

  private Long insertCategory(String name, String slug, Long parentId) {
    return jdbc.queryForObject(
      """
      INSERT INTO catalogue_category (name, slug, parent_id, sort_rank)
      VALUES (
        ?, ?, ?,
        (
          SELECT (COUNT(*) + 1) * 1000000000::BIGINT
          FROM catalogue_category
          WHERE parent_id IS NOT DISTINCT FROM ?
        )
      )
      RETURNING id
      """,
      Long.class,
      name,
      slug,
      parentId,
      parentId
    );
  }

  private void insertClosure(Long ancestorId, Long descendantId, int depth) {
    jdbc.update(
      "INSERT INTO catalogue_category_closure (ancestor_id, descendant_id, depth) VALUES (?, ?, ?)",
      ancestorId,
      descendantId,
      depth
    );
  }

  private void insertProductCategory(Long productId, Long categoryId) {
    jdbc.update(
      "INSERT INTO catalogue_product_category (product_id, category_id) VALUES (?, ?)",
      productId,
      categoryId
    );
  }

  private long productCategoryCount(Long productId, Long categoryId) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_product_category WHERE product_id = ? AND category_id = ?",
      Long.class,
      productId,
      categoryId
    );
  }

  private long productCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_product", Long.class);
  }

  private static CatalogueProduct product(
    String title,
    String slug,
    ProductFamily family,
    Long brandId,
    Long manufacturerId
  ) {
    return new CatalogueProduct(
      null,
      title,
      slug,
      family,
      new CatalogueBrand(brandId, null, null, true, null, null),
      new CatalogueManufacturer(manufacturerId, null, null, true, null, null),
      null,
      null,
      true,
      null,
      null
    );
  }

  private Long insertOffer(Long productId, Long merchantStoreId) {
    return jdbc.queryForObject(
      """
      INSERT INTO catalogue_merchant_product_offer (product_id, merchant_store_id, merchant_sku, offer_url)
      VALUES (?, ?, ?, ?) RETURNING id
      """,
      Long.class,
      productId,
      merchantStoreId,
      "SKU-" + productId,
      "https://example.test/offers/" + productId
    );
  }

  private void insertCurrentPrice(
    Long offerId,
    Long productId,
    Long merchantStoreId,
    String amount,
    String marketplace
  ) {
    jdbc.update(
      """
      INSERT INTO catalogue_product_current_price (
        merchant_product_offer_id, product_id, merchant_store_id, marketplace_country,
        currency, amount, availability, source, captured_at
      )
      VALUES (?, ?, ?, ?, 'CAD', ?, 'IN_STOCK', 'MANUAL', ?)
      """,
      offerId,
      productId,
      merchantStoreId,
      marketplace,
      new BigDecimal(amount),
      NOW
    );
  }

  private CataloguePrice price(Long offerId, String amount, OffsetDateTime capturedAt) {
    return new CataloguePrice(
      null,
      offerId,
      null,
      null,
      Marketplace.CA,
      Currency.CAD,
      new BigDecimal(amount),
      Availability.IN_STOCK,
      PriceSource.MANUAL,
      capturedAt,
      null
    );
  }

  private long historyCount(Long offerId) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_product_price_history WHERE merchant_product_offer_id = ?",
      Long.class,
      offerId
    );
  }

  private record CatalogueIds(
    Long storageProductId,
    Long unpricedStorageProductId,
    Long cpuProductId,
    Long gpuProductId,
    Long merchantStoreId,
    Long offerId,
    Long storageCategoryId,
    Long nvmeCategoryId
  ) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class ContainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
