package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class CatalogueReferenceNotFoundException extends CatalogueException {

  private final String code;

  private CatalogueReferenceNotFoundException(
    String code,
    String messageKey,
    String defaultMessage,
    Object... args
  ) {
    super(messageKey, defaultMessage, args);
    this.code = code;
  }

  public static CatalogueReferenceNotFoundException brand(Long id) {
    return new CatalogueReferenceNotFoundException(
      "BRAND_NOT_FOUND",
      "catalogue.api.product.brand.not-found",
      "product brand was not found",
      id
    );
  }

  public static CatalogueReferenceNotFoundException catalogueBrand(Long id) {
    return new CatalogueReferenceNotFoundException(
      "BRAND_NOT_FOUND",
      "catalogue.api.brand.not-found",
      "brand was not found",
      id
    );
  }

  public static CatalogueReferenceNotFoundException manufacturer(Long id) {
    return new CatalogueReferenceNotFoundException(
      "MANUFACTURER_NOT_FOUND",
      "catalogue.api.product.manufacturer.not-found",
      "product manufacturer was not found",
      id
    );
  }

  public static CatalogueReferenceNotFoundException catalogueManufacturer(Long id) {
    return new CatalogueReferenceNotFoundException(
      "MANUFACTURER_NOT_FOUND",
      "catalogue.api.manufacturer.not-found",
      "manufacturer was not found",
      id
    );
  }

  public String code() {
    return code;
  }
}
