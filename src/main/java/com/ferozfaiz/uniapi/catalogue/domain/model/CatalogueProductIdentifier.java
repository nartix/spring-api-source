package com.ferozfaiz.uniapi.catalogue.domain.model;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductIdentifierFamily;

public record CatalogueProductIdentifier(
  ProductIdentifierFamily family,
  String value,
  String canonicalGtin14,
  boolean primary
) {}
