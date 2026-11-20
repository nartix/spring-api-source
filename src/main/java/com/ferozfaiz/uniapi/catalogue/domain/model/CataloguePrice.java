package com.ferozfaiz.uniapi.catalogue.domain.model;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Availability;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Currency;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.Marketplace;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.PriceSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CataloguePrice(
  Long id,
  Long merchantProductOfferId,
  Long productId,
  Long merchantStoreId,
  Marketplace marketplace,
  Currency currency,
  BigDecimal amount,
  Availability availability,
  PriceSource source,
  OffsetDateTime capturedAt,
  OffsetDateTime updatedAt
) {}
