package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class CatalogueCategoryPlacementTargetException extends CatalogueException {

  private CatalogueCategoryPlacementTargetException(
    String messageKey,
    String defaultMessage,
    Object... messageArgs
  ) {
    super(messageKey, defaultMessage, messageArgs);
  }

  public static CatalogueCategoryPlacementTargetException invalidTarget() {
    return new CatalogueCategoryPlacementTargetException(
      "catalogue.api.category.placement.target.invalid",
      "Placement target must belong to the destination sibling group"
    );
  }

  public String code() {
    return "INVALID_CATEGORY_PLACEMENT_TARGET";
  }
}
