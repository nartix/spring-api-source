package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "catalogue_merchant_product_offer")
public class CatalogueMerchantProductOfferEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "merchant_store_id", nullable = false)
  private Long merchantStoreId;

  @Column(name = "merchant_sku")
  @Nullable
  private String merchantSku;

  @Column(name = "merchant_product_code")
  @Nullable
  private String merchantProductCode;

  @Column(name = "offer_url", length = 1000)
  @Nullable
  private String offerUrl;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  @Nullable
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @Nullable
  private OffsetDateTime updatedAt;
}
