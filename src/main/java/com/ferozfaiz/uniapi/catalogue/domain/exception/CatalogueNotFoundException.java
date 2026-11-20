package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class CatalogueNotFoundException extends CatalogueException {

  private final String code;

  private CatalogueNotFoundException(
    String code,
    String messageKey,
    String defaultMessage,
    Object... args
  ) {
    super(messageKey, defaultMessage, args);
    this.code = code;
  }

  public static CatalogueNotFoundException product(Long id) {
    return new CatalogueNotFoundException(
      "PRODUCT_NOT_FOUND",
      "catalogue.api.product.not-found",
      "product was not found",
      id
    );
  }

  public static CatalogueNotFoundException category(String slug) {
    return new CatalogueNotFoundException(
      "CATEGORY_NOT_FOUND",
      "catalogue.api.category.not-found",
      "category was not found",
      slug
    );
  }

  public String code() {
    return code;
  }
}
