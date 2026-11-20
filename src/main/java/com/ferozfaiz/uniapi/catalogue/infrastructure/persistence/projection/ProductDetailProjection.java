package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProductDetailProjection(
  ProductListItemProjection product,
  List<CategorySummaryProjection> categories,
  List<CatalogueProductIdentifier> identifiers,
  BigDecimal baseClockGhz,
  String cpuArchitecture,
  Boolean integratedGraphics,
  Integer cpuTdpWatts,
  Integer storageRpm,
  Integer storageCacheMb,
  String gpuInterfaceType,
  Integer gpuLengthMm,
  Integer gpuTdpWatts
) {
  public ProductDetailProjection {
    categories = categories == null ? List.of() : List.copyOf(categories);
    identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
  }

  public ProductDetailProjection(
    ProductListItemProjection product,
    List<CategorySummaryProjection> categories,
    BigDecimal baseClockGhz,
    String cpuArchitecture,
    Boolean integratedGraphics,
    Integer cpuTdpWatts,
    Integer storageRpm,
    Integer storageCacheMb,
    String gpuInterfaceType,
    Integer gpuLengthMm,
    Integer gpuTdpWatts
  ) {
    this(
      product,
      categories,
      List.of(),
      baseClockGhz,
      cpuArchitecture,
      integratedGraphics,
      cpuTdpWatts,
      storageRpm,
      storageCacheMb,
      gpuInterfaceType,
      gpuLengthMm,
      gpuTdpWatts
    );
  }

  public record CategorySummaryProjection(Long id, String name, String slug) {}

  public OffsetDateTime updatedAt() {
    return product.updatedAt();
  }
}
