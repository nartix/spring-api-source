package com.ferozfaiz.uniapi.catalogue.domain.model;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import java.time.OffsetDateTime;
import java.util.List;

public record CatalogueProduct(
  Long id,
  String title,
  String slug,
  ProductFamily family,
  CatalogueBrand brand,
  CatalogueManufacturer manufacturer,
  String mainImageUrl,
  String shortDescription,
  boolean active,
  List<CatalogueProductIdentifier> identifiers,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {
  public CatalogueProduct {
    identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
  }

  public CatalogueProduct(
    Long id,
    String title,
    String slug,
    ProductFamily family,
    CatalogueBrand brand,
    CatalogueManufacturer manufacturer,
    String mainImageUrl,
    String shortDescription,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
  ) {
    this(
      id,
      title,
      slug,
      family,
      brand,
      manufacturer,
      mainImageUrl,
      shortDescription,
      active,
      List.of(),
      createdAt,
      updatedAt
    );
  }
}
