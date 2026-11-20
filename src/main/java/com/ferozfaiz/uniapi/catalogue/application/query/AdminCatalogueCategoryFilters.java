package com.ferozfaiz.uniapi.catalogue.application.query;

public record AdminCatalogueCategoryFilters(Boolean active, String q, String slug) {
  public AdminCatalogueCategoryFilters {
    q = blankToNull(q);
    slug = blankToNull(slug);
  }

  public static AdminCatalogueCategoryFilters none() {
    return new AdminCatalogueCategoryFilters(null, null, null);
  }

  public boolean empty() {
    return active == null && q == null && slug == null;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
