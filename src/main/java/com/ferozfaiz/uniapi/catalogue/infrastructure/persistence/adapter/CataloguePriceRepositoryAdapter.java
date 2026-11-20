package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.domain.model.CataloguePrice;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CataloguePriceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class CataloguePriceRepositoryAdapter implements CataloguePriceRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public CataloguePriceRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public CataloguePrice recordCurrentPrice(CataloguePrice price) {
    OfferContext offer = offerContext(price.merchantProductOfferId());
    MapSqlParameterSource parameters = parameters(price, offer);
    CataloguePrice current = findCurrent(
      price.merchantProductOfferId(),
      price.marketplace().name()
    );
    if (current == null) {
      Long currentId = insertCurrent(parameters);
      insertHistory(parameters);
      return new CataloguePrice(
        currentId,
        price.merchantProductOfferId(),
        offer.productId(),
        offer.merchantStoreId(),
        price.marketplace(),
        price.currency(),
        price.amount(),
        price.availability(),
        price.source(),
        price.capturedAt(),
        price.capturedAt()
      );
    }

    updateCurrent(parameters);
    if (stateChanged(current, price, offer)) {
      insertHistory(parameters);
    }
    return findCurrent(price.merchantProductOfferId(), price.marketplace().name());
  }

  private OfferContext offerContext(Long offerId) {
    return jdbc.queryForObject(
      """
      SELECT product_id, merchant_store_id
      FROM catalogue_merchant_product_offer
      WHERE id = :offerId
      """,
      new MapSqlParameterSource("offerId", offerId),
      (rs, rowNum) -> new OfferContext(rs.getLong("product_id"), rs.getLong("merchant_store_id"))
    );
  }

  private CataloguePrice findCurrent(Long offerId, String marketplace) {
    return jdbc
      .query(
        """
        SELECT id, merchant_product_offer_id, product_id, merchant_store_id, marketplace_country,
          currency, amount, availability, source, captured_at, updated_at
        FROM catalogue_product_current_price
        WHERE merchant_product_offer_id = :offerId AND marketplace_country = :marketplace
        """,
        new MapSqlParameterSource()
          .addValue("offerId", offerId)
          .addValue("marketplace", marketplace),
        this::mapPrice
      )
      .stream()
      .findFirst()
      .orElse(null);
  }

  private Long insertCurrent(MapSqlParameterSource parameters) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(
      """
      INSERT INTO catalogue_product_current_price (
        merchant_product_offer_id, product_id, merchant_store_id, marketplace_country,
        currency, amount, availability, source, captured_at, updated_at
      )
      VALUES (
        :offerId, :productId, :merchantStoreId, :marketplace,
        :currency, :amount, :availability, :source, :capturedAt, CURRENT_TIMESTAMP
      )
      """,
      parameters,
      keyHolder,
      new String[] { "id" }
    );
    Number key = keyHolder.getKey();
    return key == null ? null : key.longValue();
  }

  private void updateCurrent(MapSqlParameterSource parameters) {
    jdbc.update(
      """
      UPDATE catalogue_product_current_price
      SET product_id = :productId,
        merchant_store_id = :merchantStoreId,
        currency = :currency,
        amount = :amount,
        availability = :availability,
        source = :source,
        captured_at = :capturedAt,
        updated_at = CURRENT_TIMESTAMP
      WHERE merchant_product_offer_id = :offerId AND marketplace_country = :marketplace
      """,
      parameters
    );
  }

  private void insertHistory(MapSqlParameterSource parameters) {
    jdbc.update(
      """
      INSERT INTO catalogue_product_price_history (
        merchant_product_offer_id, product_id, merchant_store_id, marketplace_country,
        currency, amount, availability, source, captured_at
      )
      VALUES (
        :offerId, :productId, :merchantStoreId, :marketplace,
        :currency, :amount, :availability, :source, :capturedAt
      )
      """,
      parameters
    );
  }

  private static MapSqlParameterSource parameters(CataloguePrice price, OfferContext offer) {
    return new MapSqlParameterSource()
      .addValue("offerId", price.merchantProductOfferId())
      .addValue("productId", offer.productId())
      .addValue("merchantStoreId", offer.merchantStoreId())
      .addValue("marketplace", price.marketplace().name())
      .addValue("currency", price.currency().name())
      .addValue("amount", price.amount())
      .addValue("availability", price.availability().name())
      .addValue("source", price.source().name())
      .addValue("capturedAt", price.capturedAt());
  }

  private CataloguePrice mapPrice(ResultSet rs, int rowNum) throws SQLException {
    return new CataloguePrice(
      rs.getLong("id"),
      rs.getLong("merchant_product_offer_id"),
      rs.getLong("product_id"),
      rs.getLong("merchant_store_id"),
      com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Marketplace.valueOf(
        rs.getString("marketplace_country")
      ),
      com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Currency.valueOf(
        rs.getString("currency")
      ),
      rs.getBigDecimal("amount"),
      com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Availability.valueOf(
        rs.getString("availability")
      ),
      com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.PriceSource.valueOf(
        rs.getString("source")
      ),
      rs.getObject("captured_at", java.time.OffsetDateTime.class),
      rs.getObject("updated_at", java.time.OffsetDateTime.class)
    );
  }

  private static boolean stateChanged(
    CataloguePrice current,
    CataloguePrice requested,
    OfferContext offer
  ) {
    return (
      !current.amount().stripTrailingZeros().equals(requested.amount().stripTrailingZeros()) ||
      current.currency() != requested.currency() ||
      current.availability() != requested.availability() ||
      current.source() != requested.source() ||
      !current.productId().equals(offer.productId()) ||
      !current.merchantStoreId().equals(offer.merchantStoreId())
    );
  }

  private record OfferContext(Long productId, Long merchantStoreId) {}
}
