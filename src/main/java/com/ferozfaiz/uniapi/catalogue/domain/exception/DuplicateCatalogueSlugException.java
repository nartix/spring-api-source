package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class DuplicateCatalogueSlugException extends CatalogueException {

  private final String code;

  private DuplicateCatalogueSlugException(String code, String key, String slug) {
    super(key, "duplicate catalogue slug: " + slug, slug);
    this.code = code;
  }

  public static DuplicateCatalogueSlugException brand(String slug) {
    return new DuplicateCatalogueSlugException(
      "DUPLICATE_BRAND_SLUG",
      "catalogue.domain.brand.slug.duplicate",
      slug
    );
  }

  public static DuplicateCatalogueSlugException manufacturer(String slug) {
    return new DuplicateCatalogueSlugException(
      "DUPLICATE_MANUFACTURER_SLUG",
      "catalogue.domain.manufacturer.slug.duplicate",
      slug
    );
  }

  public static DuplicateCatalogueSlugException category(String slug) {
    return new DuplicateCatalogueSlugException(
      "DUPLICATE_CATEGORY_SLUG",
      "catalogue.domain.category.slug.duplicate",
      slug
    );
  }

  public static DuplicateCatalogueSlugException product(String slug) {
    return new DuplicateCatalogueSlugException(
      "DUPLICATE_PRODUCT_SLUG",
      "catalogue.domain.product.slug.duplicate",
      slug
    );
  }

  public static DuplicateCatalogueSlugException merchantStore(String slug) {
    return new DuplicateCatalogueSlugException(
      "DUPLICATE_MERCHANT_STORE_SLUG",
      "catalogue.domain.merchant-store.slug.duplicate",
      slug
    );
  }

  public String code() {
    return code;
  }
}
