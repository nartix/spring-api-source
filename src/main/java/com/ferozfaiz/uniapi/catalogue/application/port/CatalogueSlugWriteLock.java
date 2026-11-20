package com.ferozfaiz.uniapi.catalogue.application.port;

public interface CatalogueSlugWriteLock {
  void lock(ResourceType resourceType, String slug);

  enum ResourceType {
    PRODUCT,
    CATEGORY,
    BRAND,
    MANUFACTURER,
  }
}
