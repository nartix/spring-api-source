package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.application.query.GetProductDetailQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductDetailProjection;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductListItemProjection;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.Optional;

public interface CatalogueProductListingRepository {
  PageResult<ProductListItemProjection> findActiveProducts(ListProductsQuery query);

  Optional<ProductDetailProjection> findActiveProductDetail(GetProductDetailQuery query);
}
