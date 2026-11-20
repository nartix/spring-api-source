package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProductDetailResponse(
  Long id,
  String title,
  String slug,
  String canonicalUrl,
  ProductFamily family,
  ProductListItemResponse.SummaryResponse brand,
  ProductListItemResponse.SummaryResponse manufacturer,
  List<ProductListItemResponse.SummaryResponse> categories,
  List<ProductIdentifierResponse> identifiers,
  String mainImageUrl,
  String shortDescription,
  ProductListItemResponse.PriceResponse currentPrice,
  ProductListItemResponse.OfferResponse bestOffer,
  StorageDetailResponse storage,
  CpuDetailResponse cpu,
  GraphicsCardDetailResponse graphicsCard,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {
  public record StorageDetailResponse(
    String storageType,
    Integer capacityGb,
    String interfaceType,
    String formFactor,
    Integer readSpeedMbps,
    Integer writeSpeedMbps,
    Integer rpm,
    Integer cacheMb
  ) {}

  public record CpuDetailResponse(
    String socket,
    Integer coreCount,
    Integer threadCount,
    BigDecimal baseClockGhz,
    BigDecimal boostClockGhz,
    String architecture,
    Boolean integratedGraphics,
    Integer tdpWatts
  ) {}

  public record GraphicsCardDetailResponse(
    String gpuChipset,
    Integer vramGb,
    String memoryType,
    Integer boostClockMhz,
    String interfaceType,
    Integer lengthMm,
    Integer tdpWatts
  ) {}
}
