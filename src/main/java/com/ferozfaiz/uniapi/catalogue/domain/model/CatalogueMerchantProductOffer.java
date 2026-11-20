package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.time.OffsetDateTime;

public record CatalogueMerchantProductOffer(
  Long id,
  Long productId,
  Long merchantStoreId,
  String merchantSku,
  String merchantProductCode,
  String offerUrl,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
