package com.ferozfaiz.uniapi.catalogue.application.command;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import java.util.List;

public record UpsertProductCommand(
  Long id,
  String title,
  String slug,
  ProductFamily family,
  Long brandId,
  Long manufacturerId,
  String mainImageUrl,
  String shortDescription,
  boolean active,
  List<CatalogueProductIdentifier> identifiers
) {
  public UpsertProductCommand {
    identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
  }

  public UpsertProductCommand(
    Long id,
    String title,
    String slug,
    ProductFamily family,
    Long brandId,
    Long manufacturerId,
    String mainImageUrl,
    String shortDescription,
    boolean active
  ) {
    this(
      id,
      title,
      slug,
      family,
      brandId,
      manufacturerId,
      mainImageUrl,
      shortDescription,
      active,
      List.of()
    );
  }
}
