package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductListItemResponse(
  Long id,
  String title,
  String slug,
  String canonicalUrl,
  ProductFamily family,
  SummaryResponse brand,
  SummaryResponse manufacturer,
  ProductIdentifierResponse primaryIdentifier,
  String mainImageUrl,
  String shortDescription,
  PriceResponse currentPrice,
  OfferResponse bestOffer,
  StorageSummaryResponse storage,
  CpuSummaryResponse cpu,
  GraphicsCardSummaryResponse graphicsCard,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {
  public record SummaryResponse(Long id, String name, String slug) {}

  public record PriceResponse(
    BigDecimal amount,
    String currency,
    String marketplace,
    String availability
  ) {}

  public record OfferResponse(
    Long merchantStoreId,
    String merchantStoreName,
    String merchantStoreSlug
  ) {}

  public record StorageSummaryResponse(
    String storageType,
    Integer capacityGb,
    String interfaceType,
    String formFactor
  ) {}

  public record CpuSummaryResponse(
    String socket,
    Integer coreCount,
    Integer threadCount,
    BigDecimal boostClockGhz
  ) {}

  public record GraphicsCardSummaryResponse(
    String gpuChipset,
    Integer vramGb,
    String memoryType,
    Integer boostClockMhz
  ) {}
}
