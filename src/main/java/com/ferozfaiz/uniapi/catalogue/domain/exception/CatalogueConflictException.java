package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class CatalogueConflictException extends CatalogueException {

  private final String code;

  private CatalogueConflictException(
    String code,
    String messageKey,
    String defaultMessage,
    Object... args
  ) {
    super(messageKey, defaultMessage, args);
    this.code = code;
  }

  public static CatalogueConflictException productDeleteBlocked(Long id) {
    return new CatalogueConflictException(
      "PRODUCT_DELETE_REJECTED",
      "catalogue.domain.product.delete.rejected",
      "product cannot be deleted because dependent catalogue data exists",
      id
    );
  }

  public static CatalogueConflictException categoryDeleteBlocked(Long id) {
    return new CatalogueConflictException(
      "CATEGORY_DELETE_REJECTED",
      "catalogue.domain.category.delete.rejected",
      "category cannot be deleted because dependent catalogue data exists",
      id
    );
  }

  public static CatalogueConflictException brandDeleteBlocked(Long id) {
    return new CatalogueConflictException(
      "BRAND_DELETE_REJECTED",
      "catalogue.domain.brand.delete.rejected",
      "brand cannot be deleted because dependent catalogue products exist",
      id
    );
  }

  public static CatalogueConflictException manufacturerDeleteBlocked(Long id) {
    return new CatalogueConflictException(
      "MANUFACTURER_DELETE_REJECTED",
      "catalogue.domain.manufacturer.delete.rejected",
      "manufacturer cannot be deleted because dependent catalogue products exist",
      id
    );
  }

  public static CatalogueConflictException invalidCategoryMove(Long id, Long parentId) {
    return new CatalogueConflictException(
      "CATEGORY_MOVE_REJECTED",
      "catalogue.domain.category.move.invalid",
      "category move is invalid",
      id,
      parentId
    );
  }

  public String code() {
    return code;
  }
}
