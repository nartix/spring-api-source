package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Availability;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Currency;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Marketplace;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalogue_product_price_history")
public class CatalogueProductPriceHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(name = "merchant_product_offer_id", nullable = false)
  private Long merchantProductOfferId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "merchant_store_id", nullable = false)
  private Long merchantStoreId;

  @Enumerated(EnumType.STRING)
  @Column(name = "marketplace_country", nullable = false, length = 2)
  private Marketplace marketplace;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private Availability availability;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private PriceSource source;

  @Column(name = "captured_at", nullable = false)
  private OffsetDateTime capturedAt;

  @Column(name = "created_at", nullable = false)
  @Nullable
  private OffsetDateTime createdAt;
}
