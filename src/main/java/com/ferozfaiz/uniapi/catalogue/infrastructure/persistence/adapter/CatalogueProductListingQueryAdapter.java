package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.application.query.GetProductDetailQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductIdentifierFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductDetailProjection;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductListItemProjection;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CatalogueProductListingQueryAdapter implements CatalogueProductListingRepository {

  private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
    Map.entry("id", "product.id"),
    Map.entry("title", "product.title"),
    Map.entry("brand", "brand.name"),
    Map.entry("manufacturer", "manufacturer.name"),
    Map.entry("currentPrice", "best_price.amount"),
    Map.entry("capacityGb", "storage.capacity_gb"),
    Map.entry("storageType", "storage.storage_type"),
    Map.entry("interfaceType", "COALESCE(storage.interface_type, gpu.interface_type)"),
    Map.entry("readSpeedMbps", "storage.read_speed_mbps"),
    Map.entry("writeSpeedMbps", "storage.write_speed_mbps"),
    Map.entry("coreCount", "cpu.core_count"),
    Map.entry("threadCount", "cpu.thread_count"),
    Map.entry("boostClockGhz", "cpu.boost_clock_ghz"),
    Map.entry("vramGb", "gpu.vram_gb"),
    Map.entry("createdAt", "product.created_at"),
    Map.entry("updatedAt", "product.updated_at")
  );

  private final NamedParameterJdbcTemplate jdbc;

  public CatalogueProductListingQueryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PageResult<ProductListItemProjection> findActiveProducts(ListProductsQuery query) {
    MapSqlParameterSource parameters = parameters(query)
      .addValue("limit", query.page().size())
      .addValue("offset", (long) query.page().page() * query.page().size());
    String where = whereClause(query);
    long total = count(where, parameters);
    List<ProductListItemProjection> content = jdbc.query(
      selectSql(where, orderBy(query.sort())) + " LIMIT :limit OFFSET :offset",
      parameters,
      this::mapListItem
    );
    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / query.page().size());
    boolean first = query.page().page() == 0;
    boolean last = totalPages == 0 || query.page().page() >= totalPages - 1;
    return new PageResult<>(
      content,
      query.page().page(),
      query.page().size(),
      total,
      totalPages,
      first,
      last,
      query.sort()
    );
  }

  @Override
  public Optional<ProductDetailProjection> findActiveProductDetail(GetProductDetailQuery query) {
    MapSqlParameterSource parameters = new MapSqlParameterSource()
      .addValue("id", query.id())
      .addValue("slug", query.slug())
      .addValue("marketplace", query.marketplace())
      .addValue("merchantStore", query.merchantStore());
    String where =
      "WHERE product.is_active = true AND product.id = :id" +
      (query.slug() == null ? "" : " AND product.slug = :slug");
    List<ProductDetailProjection> products = jdbc.query(
      selectSql(where, "ORDER BY product.id ASC"),
      parameters,
      this::mapDetail
    );
    if (products.isEmpty()) {
      return Optional.empty();
    }
    ProductDetailProjection product = products.getFirst();
    List<ProductDetailProjection.CategorySummaryProjection> categories = jdbc.query(
      """
      SELECT category.id, category.name, category.slug
      FROM catalogue_product_category product_category
      JOIN catalogue_category category ON category.id = product_category.category_id
      WHERE product_category.product_id = :id AND category.is_active = true
      ORDER BY category.name ASC, category.id ASC
      """,
      parameters,
      (rs, rowNum) ->
        new ProductDetailProjection.CategorySummaryProjection(
          rs.getLong("id"),
          rs.getString("name"),
          rs.getString("slug")
        )
    );
    List<CatalogueProductIdentifier> identifiers = jdbc.query(
      """
      SELECT family, value, canonical_gtin14, is_primary
      FROM catalogue_product_identifier
      WHERE product_id = :id
      ORDER BY is_primary DESC, id ASC
      """,
      parameters,
      (rs, rowNum) -> mapIdentifier(rs, "")
    );
    return Optional.of(
      new ProductDetailProjection(
        product.product(),
        categories,
        identifiers,
        product.baseClockGhz(),
        product.cpuArchitecture(),
        product.integratedGraphics(),
        product.cpuTdpWatts(),
        product.storageRpm(),
        product.storageCacheMb(),
        product.gpuInterfaceType(),
        product.gpuLengthMm(),
        product.gpuTdpWatts()
      )
    );
  }

  private long count(String where, MapSqlParameterSource parameters) {
    Long count = jdbc.queryForObject(
      "SELECT COUNT(*) FROM (" + baseSql(where) + ") counted_products",
      parameters,
      Long.class
    );
    return count == null ? 0 : count;
  }

  private static MapSqlParameterSource parameters(ListProductsQuery query) {
    MapSqlParameterSource parameters = new MapSqlParameterSource()
      .addValue("family", query.family() == null ? null : query.family().name())
      .addValue("categorySlug", query.categorySlug())
      .addValue("brand", query.brand())
      .addValue("manufacturer", query.manufacturer())
      .addValue("merchantStore", query.merchantStore())
      .addValue("marketplace", query.marketplace())
      .addValue("hasCurrentPrice", query.hasCurrentPrice())
      .addValue(
        "search",
        query.search() == null ? null : "%" + query.search().toLowerCase(Locale.ROOT) + "%"
      );
    query.familyFilters().forEach(parameters::addValue);
    return parameters;
  }

  private static String whereClause(ListProductsQuery query) {
    List<String> predicates = new java.util.ArrayList<>();
    predicates.add("product.is_active = true");
    add(query.family() != null, predicates, "product.product_family = :family");
    add(query.categorySlug() != null, predicates, "category_filter.slug = :categorySlug");
    add(query.brand() != null, predicates, "brand.slug = :brand");
    add(query.manufacturer() != null, predicates, "manufacturer.slug = :manufacturer");
    add(
      query.merchantStore() != null,
      predicates,
      "best_price.merchant_store_slug = :merchantStore"
    );
    add(
      query.hasCurrentPrice() != null && query.hasCurrentPrice(),
      predicates,
      "best_price.amount IS NOT NULL"
    );
    add(
      query.hasCurrentPrice() != null && !query.hasCurrentPrice(),
      predicates,
      "best_price.amount IS NULL"
    );
    add(
      query.search() != null,
      predicates,
      """
      (
        LOWER(product.title) LIKE :search OR LOWER(brand.name) LIKE :search OR LOWER(manufacturer.name) LIKE :search
        OR LOWER(COALESCE(storage.storage_type, '')) LIKE :search OR LOWER(COALESCE(cpu.architecture, '')) LIKE :search
        OR LOWER(COALESCE(gpu.gpu_chipset, '')) LIKE :search
      )
      """
    );
    add(
      query.familyFilters().get("storageType") != null,
      predicates,
      "storage.storage_type = :storageType"
    );
    add(
      query.familyFilters().get("capacityGbMin") != null,
      predicates,
      "storage.capacity_gb >= :capacityGbMin"
    );
    add(
      query.familyFilters().get("capacityGbMax") != null,
      predicates,
      "storage.capacity_gb <= :capacityGbMax"
    );
    add(
      query.familyFilters().get("interfaceType") != null,
      predicates,
      "(storage.interface_type = :interfaceType OR gpu.interface_type = :interfaceType)"
    );
    add(
      query.familyFilters().get("formFactor") != null,
      predicates,
      "storage.form_factor = :formFactor"
    );
    add(
      query.familyFilters().get("readSpeedMbpsMin") != null,
      predicates,
      "storage.read_speed_mbps >= :readSpeedMbpsMin"
    );
    add(
      query.familyFilters().get("writeSpeedMbpsMin") != null,
      predicates,
      "storage.write_speed_mbps >= :writeSpeedMbpsMin"
    );
    add(query.familyFilters().get("rpmMin") != null, predicates, "storage.rpm >= :rpmMin");
    add(
      query.familyFilters().get("cacheMbMin") != null,
      predicates,
      "storage.cache_mb >= :cacheMbMin"
    );
    add(query.familyFilters().get("socket") != null, predicates, "cpu.socket = :socket");
    add(
      query.familyFilters().get("coreCountMin") != null,
      predicates,
      "cpu.core_count >= :coreCountMin"
    );
    add(
      query.familyFilters().get("threadCountMin") != null,
      predicates,
      "cpu.thread_count >= :threadCountMin"
    );
    add(
      query.familyFilters().get("baseClockGhzMin") != null,
      predicates,
      "cpu.base_clock_ghz >= :baseClockGhzMin"
    );
    add(
      query.familyFilters().get("boostClockGhzMin") != null,
      predicates,
      "cpu.boost_clock_ghz >= :boostClockGhzMin"
    );
    add(
      query.familyFilters().get("architecture") != null,
      predicates,
      "cpu.architecture = :architecture"
    );
    add(
      query.familyFilters().get("integratedGraphics") != null,
      predicates,
      "cpu.integrated_graphics = :integratedGraphics"
    );
    add(
      query.familyFilters().get("tdpWattsMax") != null,
      predicates,
      "(cpu.tdp_watts <= :tdpWattsMax OR gpu.tdp_watts <= :tdpWattsMax)"
    );
    add(
      query.familyFilters().get("gpuChipset") != null,
      predicates,
      "gpu.gpu_chipset = :gpuChipset"
    );
    add(query.familyFilters().get("vramGbMin") != null, predicates, "gpu.vram_gb >= :vramGbMin");
    add(
      query.familyFilters().get("memoryType") != null,
      predicates,
      "gpu.memory_type = :memoryType"
    );
    add(
      query.familyFilters().get("boostClockMhzMin") != null,
      predicates,
      "gpu.boost_clock_mhz >= :boostClockMhzMin"
    );
    add(
      query.familyFilters().get("lengthMmMax") != null,
      predicates,
      "gpu.length_mm <= :lengthMmMax"
    );
    return "WHERE " + String.join(" AND ", predicates);
  }

  private static void add(boolean condition, List<String> predicates, String predicate) {
    if (condition) {
      predicates.add(predicate);
    }
  }

  private static String orderBy(SortCriteria sort) {
    List<String> orders = sort
      .orders()
      .stream()
      .map(CatalogueProductListingQueryAdapter::orderBy)
      .toList();
    return "ORDER BY " + String.join(", ", orders);
  }

  private static String orderBy(SortOrder order) {
    String column = SORT_COLUMNS.get(order.property());
    if (column == null) {
      throw new InvalidRequestException(
        "catalogue.api.list.sort.allowed",
        "invalid sort",
        String.join(", ", SORT_COLUMNS.keySet())
      );
    }
    String direction = order.direction() == SortDirection.DESC ? "DESC" : "ASC";
    String nulls = order.direction() == SortDirection.DESC ? "NULLS LAST" : "NULLS LAST";
    return column + " " + direction + " " + nulls;
  }

  private static String selectSql(String where, String orderBy) {
    return baseSql(where) + " " + orderBy;
  }

  private static String baseSql(String where) {
    return (
      """
      SELECT
        product.id, product.title, product.slug, product.product_family, product.main_image_url, product.short_description,
        product.created_at, product.updated_at,
        brand.id AS brand_id, brand.name AS brand_name, brand.slug AS brand_slug,
        manufacturer.id AS manufacturer_id, manufacturer.name AS manufacturer_name, manufacturer.slug AS manufacturer_slug,
        primary_identifier.family AS primary_identifier_family,
        primary_identifier.value AS primary_identifier_value,
        primary_identifier.canonical_gtin14 AS primary_identifier_canonical_gtin14,
        primary_identifier.is_primary AS primary_identifier_is_primary,
        storage.storage_type, storage.capacity_gb, storage.interface_type AS storage_interface_type, storage.form_factor,
        storage.read_speed_mbps, storage.write_speed_mbps, storage.rpm, storage.cache_mb,
        cpu.socket, cpu.core_count, cpu.thread_count, cpu.base_clock_ghz, cpu.boost_clock_ghz,
        cpu.architecture, cpu.integrated_graphics, cpu.tdp_watts AS cpu_tdp_watts,
        gpu.gpu_chipset, gpu.vram_gb, gpu.memory_type, gpu.boost_clock_mhz, gpu.interface_type AS gpu_interface_type,
        gpu.length_mm, gpu.tdp_watts AS gpu_tdp_watts,
        best_price.amount, best_price.currency, best_price.marketplace_country, best_price.availability,
        best_price.merchant_store_id, best_price.merchant_store_name, best_price.merchant_store_slug
      FROM catalogue_product product
      JOIN catalogue_brand brand ON brand.id = product.brand_id
      JOIN catalogue_manufacturer manufacturer ON manufacturer.id = product.manufacturer_id
      LEFT JOIN catalogue_storage_product storage ON storage.product_id = product.id
      LEFT JOIN catalogue_cpu_product cpu ON cpu.product_id = product.id
      LEFT JOIN catalogue_graphics_card_product gpu ON gpu.product_id = product.id
      LEFT JOIN catalogue_product_category product_category_filter ON product_category_filter.product_id = product.id
      LEFT JOIN catalogue_category_closure category_closure_filter ON category_closure_filter.descendant_id = product_category_filter.category_id
      LEFT JOIN catalogue_category category_filter ON category_filter.id = category_closure_filter.ancestor_id
      LEFT JOIN LATERAL (
        SELECT identifier.family, identifier.value, identifier.canonical_gtin14, identifier.is_primary
        FROM catalogue_product_identifier identifier
        WHERE identifier.product_id = product.id
        ORDER BY identifier.is_primary DESC, identifier.id ASC
        LIMIT 1
      ) primary_identifier ON true
      LEFT JOIN LATERAL (
        SELECT price.amount, price.currency, price.marketplace_country, price.availability,
          store.id AS merchant_store_id, store.name AS merchant_store_name, store.slug AS merchant_store_slug
        FROM catalogue_product_current_price price
        JOIN catalogue_merchant_store store ON store.id = price.merchant_store_id
        WHERE price.product_id = product.id
          AND price.marketplace_country = :marketplace
          AND store.slug = COALESCE(:merchantStore, store.slug)
        ORDER BY CASE WHEN price.availability = 'IN_STOCK' THEN 0 ELSE 1 END, price.amount ASC, price.id ASC
        LIMIT 1
      ) best_price ON true
      """ +
      where +
      """
       GROUP BY product.id, brand.id, manufacturer.id, storage.product_id, cpu.product_id, gpu.product_id,
        primary_identifier.family, primary_identifier.value, primary_identifier.canonical_gtin14,
        primary_identifier.is_primary,
        best_price.amount, best_price.currency, best_price.marketplace_country, best_price.availability,
        best_price.merchant_store_id, best_price.merchant_store_name, best_price.merchant_store_slug
      """
    );
  }

  private ProductListItemProjection mapListItem(ResultSet rs, int rowNum) throws SQLException {
    return new ProductListItemProjection(
      rs.getLong("id"),
      rs.getString("title"),
      rs.getString("slug"),
      ProductFamily.valueOf(rs.getString("product_family")),
      rs.getLong("brand_id"),
      rs.getString("brand_name"),
      rs.getString("brand_slug"),
      rs.getLong("manufacturer_id"),
      rs.getString("manufacturer_name"),
      rs.getString("manufacturer_slug"),
      mapIdentifier(rs, "primary_identifier_"),
      rs.getString("main_image_url"),
      rs.getString("short_description"),
      rs.getBigDecimal("amount"),
      rs.getString("currency"),
      rs.getString("marketplace_country"),
      rs.getString("availability"),
      nullableLong(rs, "merchant_store_id"),
      rs.getString("merchant_store_name"),
      rs.getString("merchant_store_slug"),
      rs.getString("storage_type"),
      nullableInteger(rs, "capacity_gb"),
      rs.getString("storage_interface_type"),
      rs.getString("form_factor"),
      nullableInteger(rs, "read_speed_mbps"),
      nullableInteger(rs, "write_speed_mbps"),
      rs.getString("socket"),
      nullableInteger(rs, "core_count"),
      nullableInteger(rs, "thread_count"),
      rs.getBigDecimal("boost_clock_ghz"),
      rs.getString("gpu_chipset"),
      nullableInteger(rs, "vram_gb"),
      rs.getString("memory_type"),
      nullableInteger(rs, "boost_clock_mhz"),
      rs.getObject("created_at", java.time.OffsetDateTime.class),
      rs.getObject("updated_at", java.time.OffsetDateTime.class)
    );
  }

  private ProductDetailProjection mapDetail(ResultSet rs, int rowNum) throws SQLException {
    return new ProductDetailProjection(
      mapListItem(rs, rowNum),
      List.of(),
      rs.getBigDecimal("base_clock_ghz"),
      rs.getString("architecture"),
      nullableBoolean(rs, "integrated_graphics"),
      nullableInteger(rs, "cpu_tdp_watts"),
      nullableInteger(rs, "rpm"),
      nullableInteger(rs, "cache_mb"),
      rs.getString("gpu_interface_type"),
      nullableInteger(rs, "length_mm"),
      nullableInteger(rs, "gpu_tdp_watts")
    );
  }

  private static CatalogueProductIdentifier mapIdentifier(ResultSet rs, String prefix)
    throws SQLException {
    String family = rs.getString(prefix + "family");
    if (family == null) {
      return null;
    }
    return new CatalogueProductIdentifier(
      ProductIdentifierFamily.valueOf(family),
      rs.getString(prefix + "value"),
      rs.getString(prefix + "canonical_gtin14"),
      rs.getBoolean(prefix + "is_primary")
    );
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
    boolean value = rs.getBoolean(column);
    return rs.wasNull() ? null : value;
  }

  private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }
}
