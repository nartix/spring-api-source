package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.application.query.GetProductDetailQuery;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductDetailProjection;
import java.util.Optional;

/**
 * Detail lookup is implemented by {@link CatalogueProductListingQueryAdapter} so listing and detail
 * keep identical current-price selection semantics. This type names the dedicated detail adapter
 * surface from the feature plan.
 */
public final class CatalogueProductDetailQueryAdapter {

  private final CatalogueProductListingQueryAdapter delegate;

  public CatalogueProductDetailQueryAdapter(CatalogueProductListingQueryAdapter delegate) {
    this.delegate = delegate;
  }

  public Optional<ProductDetailProjection> findActiveProductDetail(GetProductDetailQuery query) {
    return delegate.findActiveProductDetail(query);
  }
}
