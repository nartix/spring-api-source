package com.ferozfaiz.uniapi.catalogue.application.query;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.util.Map;

public record ListProductsQuery(
  PageCriteria page,
  SortCriteria sort,
  String search,
  ProductFamily family,
  String categorySlug,
  String brand,
  String manufacturer,
  String merchantStore,
  String marketplace,
  Boolean hasCurrentPrice,
  Map<String, Object> familyFilters
) {
  public ListProductsQuery {
    familyFilters = Map.copyOf(familyFilters);
  }
}
