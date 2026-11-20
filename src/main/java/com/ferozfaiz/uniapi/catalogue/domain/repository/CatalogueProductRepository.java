package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import java.util.Optional;

public interface CatalogueProductRepository {
  CatalogueProduct save(CatalogueProduct product);

  Optional<CatalogueProduct> findById(Long id);

  Optional<CatalogueProduct> findActiveById(Long id);

  boolean existsSlug(String slug, Long excludingId);

  boolean existsIdentifier(String canonicalGtin14, Long excludingProductId);

  boolean categoryExists(Long categoryId);

  boolean hasMerchantOffers(Long productId);

  boolean hasCurrentPrices(Long productId);

  boolean hasPriceHistory(Long productId);

  void assignCategory(Long productId, Long categoryId);

  void removeCategory(Long productId, Long categoryId);

  void deleteById(Long id);
}
