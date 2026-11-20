package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
  properties = {
    "app.security.oauth2.clients.nextjs-server.scopes[0]=api",
    "app.security.oauth2.clients.nextjs-server.scopes[1]=catalogue:read",
    "app.security.oauth2.clients.nextjs-server.scopes[2]=catalogue:write",
  }
)
@Import(CataloguePersistenceTestContainersConfiguration.class)
abstract class AbstractCataloguePersistenceIntegrationTest {

  protected static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T10:15:30Z");

  @Autowired
  protected JdbcTemplate jdbc;

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

  protected Long insertBrand(String name, String slug) {
    return jdbc.queryForObject(
      "INSERT INTO catalogue_brand (name, slug) VALUES (?, ?) RETURNING id",
      Long.class,
      name,
      slug
    );
  }

  protected Long insertManufacturer(String name, String slug) {
    return jdbc.queryForObject(
      "INSERT INTO catalogue_manufacturer (name, slug) VALUES (?, ?) RETURNING id",
      Long.class,
      name,
      slug
    );
  }

  protected Long insertCategory(String name, String slug, Long parentId) {
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

  protected void insertClosure(Long ancestorId, Long descendantId, int depth) {
    jdbc.update(
      "INSERT INTO catalogue_category_closure (ancestor_id, descendant_id, depth) VALUES (?, ?, ?)",
      ancestorId,
      descendantId,
      depth
    );
  }

  protected Long insertProduct(
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

  protected Long insertMerchantStore(String name, String slug) {
    return jdbc.queryForObject(
      "INSERT INTO catalogue_merchant_store (name, slug) VALUES (?, ?) RETURNING id",
      Long.class,
      name,
      slug
    );
  }

  protected Long insertOffer(Long productId, Long merchantStoreId) {
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

  protected void insertCurrentPrice(Long offerId, Long productId, Long merchantStoreId) {
    jdbc.update(
      """
      INSERT INTO catalogue_product_current_price (
        merchant_product_offer_id, product_id, merchant_store_id, marketplace_country,
        currency, amount, availability, source, captured_at
      )
      VALUES (?, ?, ?, 'CA', 'CAD', ?, 'IN_STOCK', 'MANUAL', ?)
      """,
      offerId,
      productId,
      merchantStoreId,
      new BigDecimal("149.99"),
      NOW
    );
  }

  protected void insertPriceHistory(Long offerId, Long productId, Long merchantStoreId) {
    jdbc.update(
      """
      INSERT INTO catalogue_product_price_history (
        merchant_product_offer_id, product_id, merchant_store_id, marketplace_country,
        currency, amount, availability, source, captured_at
      )
      VALUES (?, ?, ?, 'CA', 'CAD', ?, 'IN_STOCK', 'MANUAL', ?)
      """,
      offerId,
      productId,
      merchantStoreId,
      new BigDecimal("149.99"),
      NOW
    );
  }

  protected long countRows(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }
}
