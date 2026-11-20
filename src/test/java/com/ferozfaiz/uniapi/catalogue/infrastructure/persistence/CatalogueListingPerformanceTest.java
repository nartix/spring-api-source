package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
  properties = {
    "app.security.oauth2.clients.nextjs-server.scopes[0]=api",
    "app.security.oauth2.clients.nextjs-server.scopes[1]=catalogue:read",
  }
)
@Import(CatalogueRepositoryIntegrationTest.ContainersConfiguration.class)
@Transactional
class CatalogueListingPerformanceTest {

  private static final int PRODUCT_COUNT = 50_000;
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T10:15:30Z");

  @Autowired
  private CatalogueProductListingRepository products;

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
  void listingRepositoryPagesThroughFiftyThousandActiveProducts() {
    Long brandId = insertBrand("Fixture Brand", "fixture-brand");
    Long manufacturerId = insertManufacturer("Fixture Manufacturer", "fixture-manufacturer");
    insertProducts(brandId, manufacturerId);

    var page = products.findActiveProducts(
      new ListProductsQuery(
        new PageCriteria(0, 24),
        new SortCriteria(
          List.of(new SortOrder("title", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
        ),
        null,
        ProductFamily.STORAGE,
        null,
        null,
        null,
        null,
        "CA",
        null,
        Map.of()
      )
    );

    assertThat(page.totalElements()).isEqualTo(PRODUCT_COUNT);
    assertThat(page.content()).hasSize(24);
    assertThat(page.content().getFirst().title()).isEqualTo("Fixture Product 00001");
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

  private void insertProducts(Long brandId, Long manufacturerId) {
    List<Integer> productNumbers = IntStream.rangeClosed(1, PRODUCT_COUNT).boxed().toList();
    jdbc.batchUpdate(
      """
      INSERT INTO catalogue_product (
        title, slug, product_family, brand_id, manufacturer_id, is_active, created_at, updated_at
      )
      VALUES (?, ?, 'STORAGE', ?, ?, true, ?, ?)
      """,
      new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement ps, int index) throws SQLException {
          int number = productNumbers.get(index);
          String suffix = "%05d".formatted(number);
          ps.setString(1, "Fixture Product " + suffix);
          ps.setString(2, "fixture-product-" + suffix);
          ps.setLong(3, brandId);
          ps.setLong(4, manufacturerId);
          ps.setObject(5, NOW);
          ps.setObject(6, NOW);
        }

        @Override
        public int getBatchSize() {
          return productNumbers.size();
        }
      }
    );
  }
}
