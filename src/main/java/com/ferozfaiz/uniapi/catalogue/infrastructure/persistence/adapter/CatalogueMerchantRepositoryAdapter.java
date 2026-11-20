package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantProductOffer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantStore;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueMerchantRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CatalogueMerchantRepositoryAdapter implements CatalogueMerchantRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public CatalogueMerchantRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public CatalogueMerchantStore save(CatalogueMerchantStore store) {
    Long id = store.id();
    if (id == null) {
      KeyHolder keyHolder = new GeneratedKeyHolder();
      jdbc.update(
        "INSERT INTO catalogue_merchant_store (name, slug, is_active) VALUES (:name, :slug, :active)",
        storeParameters(store),
        keyHolder,
        new String[] { "id" }
      );
      Number key = keyHolder.getKey();
      id = key == null ? null : key.longValue();
    } else {
      jdbc.update(
        """
        UPDATE catalogue_merchant_store
        SET name = :name, slug = :slug, is_active = :active, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """,
        storeParameters(store)
      );
    }
    return findStore(id);
  }

  @Override
  @Transactional
  public CatalogueMerchantProductOffer saveOffer(CatalogueMerchantProductOffer offer) {
    Long id = offer.id();
    if (id == null) {
      KeyHolder keyHolder = new GeneratedKeyHolder();
      jdbc.update(
        """
        INSERT INTO catalogue_merchant_product_offer (
          product_id, merchant_store_id, merchant_sku, merchant_product_code, offer_url, is_active
        )
        VALUES (:productId, :merchantStoreId, :merchantSku, :merchantProductCode, :offerUrl, :active)
        """,
        offerParameters(offer),
        keyHolder,
        new String[] { "id" }
      );
      Number key = keyHolder.getKey();
      id = key == null ? null : key.longValue();
    } else {
      jdbc.update(
        """
        UPDATE catalogue_merchant_product_offer
        SET product_id = :productId,
          merchant_store_id = :merchantStoreId,
          merchant_sku = :merchantSku,
          merchant_product_code = :merchantProductCode,
          offer_url = :offerUrl,
          is_active = :active,
          updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """,
        offerParameters(offer)
      );
    }
    return findOffer(id);
  }

  @Override
  public boolean existsStoreSlug(String slug, Long excludingId) {
    String idPredicate = excludingId == null ? "" : " AND id <> :excludingId";
    Boolean exists = jdbc.queryForObject(
      """
      SELECT EXISTS (
        SELECT 1
        FROM catalogue_merchant_store
        WHERE slug = :slug
          %s
      )
      """.formatted(idPredicate),
      new MapSqlParameterSource().addValue("slug", slug).addValue("excludingId", excludingId),
      Boolean.class
    );
    return Boolean.TRUE.equals(exists);
  }

  private CatalogueMerchantStore findStore(Long id) {
    return jdbc.queryForObject(
      """
      SELECT id, name, slug, is_active, created_at, updated_at
      FROM catalogue_merchant_store
      WHERE id = :id
      """,
      new MapSqlParameterSource("id", id),
      (rs, rowNum) ->
        new CatalogueMerchantStore(
          rs.getLong("id"),
          rs.getString("name"),
          rs.getString("slug"),
          rs.getBoolean("is_active"),
          rs.getObject("created_at", java.time.OffsetDateTime.class),
          rs.getObject("updated_at", java.time.OffsetDateTime.class)
        )
    );
  }

  private CatalogueMerchantProductOffer findOffer(Long id) {
    return jdbc.queryForObject(
      """
      SELECT id, product_id, merchant_store_id, merchant_sku, merchant_product_code, offer_url,
        is_active, created_at, updated_at
      FROM catalogue_merchant_product_offer
      WHERE id = :id
      """,
      new MapSqlParameterSource("id", id),
      (rs, rowNum) ->
        new CatalogueMerchantProductOffer(
          rs.getLong("id"),
          rs.getLong("product_id"),
          rs.getLong("merchant_store_id"),
          rs.getString("merchant_sku"),
          rs.getString("merchant_product_code"),
          rs.getString("offer_url"),
          rs.getBoolean("is_active"),
          rs.getObject("created_at", java.time.OffsetDateTime.class),
          rs.getObject("updated_at", java.time.OffsetDateTime.class)
        )
    );
  }

  private static MapSqlParameterSource storeParameters(CatalogueMerchantStore store) {
    return new MapSqlParameterSource()
      .addValue("id", store.id())
      .addValue("name", store.name())
      .addValue("slug", store.slug())
      .addValue("active", store.active());
  }

  private static MapSqlParameterSource offerParameters(CatalogueMerchantProductOffer offer) {
    return new MapSqlParameterSource()
      .addValue("id", offer.id())
      .addValue("productId", offer.productId())
      .addValue("merchantStoreId", offer.merchantStoreId())
      .addValue("merchantSku", offer.merchantSku())
      .addValue("merchantProductCode", offer.merchantProductCode())
      .addValue("offerUrl", offer.offerUrl())
      .addValue("active", offer.active());
  }
}
