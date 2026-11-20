package com.ferozfaiz.uniapi.catalogue.application.policy;

import com.ferozfaiz.uniapi.catalogue.api.CatalogueAuthorities;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("catalogueReadAuthorizationPolicy")
public class CatalogueReadAuthorizationPolicy {

  public boolean canReadProducts(Authentication authentication) {
    return CatalogueAuthorities.hasCatalogueRead(authentication);
  }

  public boolean canReadCategoryProducts(Authentication authentication, String categorySlug) {
    return canReadProducts(authentication);
  }

  public boolean canReadProduct(Authentication authentication, Long productId) {
    return canReadProducts(authentication);
  }

  public boolean canReadBrand(Authentication authentication, Long brandId) {
    return canReadProducts(authentication);
  }

  public boolean canReadManufacturer(Authentication authentication, Long manufacturerId) {
    return canReadProducts(authentication);
  }
}
