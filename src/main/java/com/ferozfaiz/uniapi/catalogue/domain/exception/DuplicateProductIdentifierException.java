package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class DuplicateProductIdentifierException extends CatalogueException {

  public DuplicateProductIdentifierException(String canonicalGtin14) {
    super(
      "catalogue.domain.product.identifier.duplicate",
      "product identifier already exists",
      canonicalGtin14
    );
  }

  public String code() {
    return "DUPLICATE_PRODUCT_IDENTIFIER";
  }
}
