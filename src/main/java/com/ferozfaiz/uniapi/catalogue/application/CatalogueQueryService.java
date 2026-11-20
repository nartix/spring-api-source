package com.ferozfaiz.uniapi.catalogue.application;

import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.application.query.GetProductDetailQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductDetailProjection;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductListItemProjection;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogueQueryService {

  private final CatalogueProductListingRepository products;
  private final CatalogueListPolicy policy;
  private final MeterRegistry meterRegistry;

  public CatalogueQueryService(
    CatalogueProductListingRepository products,
    CatalogueListPolicy policy,
    MeterRegistry meterRegistry
  ) {
    this.products = products;
    this.policy = policy;
    this.meterRegistry = meterRegistry;
  }

  @PreAuthorize("@catalogueReadAuthorizationPolicy.canReadProducts(authentication)")
  public PageResult<ProductListItemProjection> listProducts(ListProductsQuery query) {
    policy.enforce(
      query.page(),
      query.sort(),
      query.search(),
      query.family(),
      query.familyFilters()
    );
    return Timer.builder("catalogue.listing.query")
      .description("Catalogue listing query latency")
      .register(meterRegistry)
      .record(() -> products.findActiveProducts(query));
  }

  @PreAuthorize(
    "@catalogueReadAuthorizationPolicy.canReadCategoryProducts(authentication, #categorySlug)"
  )
  public PageResult<ProductListItemProjection> listProductsInCategory(
    String categorySlug,
    ListProductsQuery query
  ) {
    ListProductsQuery categoryQuery = new ListProductsQuery(
      query.page(),
      query.sort(),
      query.search(),
      query.family(),
      categorySlug,
      query.brand(),
      query.manufacturer(),
      query.merchantStore(),
      query.marketplace(),
      query.hasCurrentPrice(),
      query.familyFilters()
    );
    return listProducts(categoryQuery);
  }

  @PreAuthorize("@catalogueReadAuthorizationPolicy.canReadProduct(authentication, #id)")
  public ProductDetailProjection productDetail(
    Long id,
    String slug,
    String marketplace,
    String merchantStore
  ) {
    return Timer.builder("catalogue.detail.query")
      .description("Catalogue detail query latency")
      .register(meterRegistry)
      .record(() ->
        products
          .findActiveProductDetail(new GetProductDetailQuery(id, slug, marketplace, merchantStore))
          .orElseThrow(() -> CatalogueNotFoundException.product(id))
      );
  }

  public ListProductsQuery withFamily(ListProductsQuery query, ProductFamily family) {
    return new ListProductsQuery(
      query.page(),
      query.sort(),
      query.search(),
      family,
      query.categorySlug(),
      query.brand(),
      query.manufacturer(),
      query.merchantStore(),
      query.marketplace(),
      query.hasCurrentPrice(),
      Map.copyOf(query.familyFilters())
    );
  }
}
