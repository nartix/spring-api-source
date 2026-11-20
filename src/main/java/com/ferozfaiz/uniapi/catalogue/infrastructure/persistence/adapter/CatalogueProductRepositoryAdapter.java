package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateProductIdentifierException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.mapper.CataloguePersistenceMapper;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueBrandJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueCategoryJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueManufacturerJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueMerchantProductOfferJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductCategoryJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductCurrentPriceJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductIdentifierJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductPriceHistoryJpaRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CatalogueProductRepositoryAdapter implements CatalogueProductRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final CatalogueProductJpaRepository products;
  private final CatalogueCategoryJpaRepository categories;
  private final CatalogueBrandJpaRepository brands;
  private final CatalogueManufacturerJpaRepository manufacturers;
  private final CatalogueProductCategoryJpaRepository productCategories;
  private final CatalogueMerchantProductOfferJpaRepository merchantProductOffers;
  private final CatalogueProductCurrentPriceJpaRepository currentPrices;
  private final CatalogueProductPriceHistoryJpaRepository priceHistory;
  private final CatalogueProductIdentifierJpaRepository productIdentifiers;

  public CatalogueProductRepositoryAdapter(
    NamedParameterJdbcTemplate jdbc,
    CatalogueProductJpaRepository products,
    CatalogueCategoryJpaRepository categories,
    CatalogueBrandJpaRepository brands,
    CatalogueManufacturerJpaRepository manufacturers,
    CatalogueProductCategoryJpaRepository productCategories,
    CatalogueMerchantProductOfferJpaRepository merchantProductOffers,
    CatalogueProductCurrentPriceJpaRepository currentPrices,
    CatalogueProductPriceHistoryJpaRepository priceHistory,
    CatalogueProductIdentifierJpaRepository productIdentifiers
  ) {
    this.jdbc = jdbc;
    this.products = products;
    this.categories = categories;
    this.brands = brands;
    this.manufacturers = manufacturers;
    this.productCategories = productCategories;
    this.merchantProductOffers = merchantProductOffers;
    this.currentPrices = currentPrices;
    this.priceHistory = priceHistory;
    this.productIdentifiers = productIdentifiers;
  }

  @Override
  @Transactional
  public CatalogueProduct save(CatalogueProduct product) {
    try {
      CatalogueProductEntity entity =
        product.id() == null
          ? CataloguePersistenceMapper.toEntity(product)
          : updateExistingEntity(product);
      CatalogueProductEntity saved = products.saveAndFlush(entity);
      replaceIdentifiers(saved.getId(), product);
      return toDomain(saved);
    } catch (DataIntegrityViolationException exception) {
      throw translateReferenceIntegrityFailure(product, exception);
    }
  }

  @Override
  public Optional<CatalogueProduct> findById(Long id) {
    return products.findById(id).map(this::toDomain);
  }

  @Override
  public Optional<CatalogueProduct> findActiveById(Long id) {
    return products.findByIdAndActiveTrue(id).map(this::toDomain);
  }

  @Override
  public boolean existsSlug(String slug, Long excludingId) {
    return excludingId == null
      ? products.existsBySlug(slug)
      : products.existsBySlugAndIdNot(slug, excludingId);
  }

  @Override
  public boolean existsIdentifier(String canonicalGtin14, Long excludingProductId) {
    return excludingProductId == null
      ? productIdentifiers.existsByCanonicalGtin14(canonicalGtin14)
      : productIdentifiers.existsByCanonicalGtin14AndProductIdNot(
          canonicalGtin14,
          excludingProductId
        );
  }

  @Override
  public boolean categoryExists(Long categoryId) {
    return categories.existsById(categoryId);
  }

  @Override
  public boolean hasMerchantOffers(Long productId) {
    return merchantProductOffers.existsByProductId(productId);
  }

  @Override
  public boolean hasCurrentPrices(Long productId) {
    return currentPrices.existsByProductId(productId);
  }

  @Override
  public boolean hasPriceHistory(Long productId) {
    return priceHistory.existsByProductId(productId);
  }

  @Override
  @Transactional
  public void assignCategory(Long productId, Long categoryId) {
    jdbc.update(
      """
      INSERT INTO catalogue_product_category (product_id, category_id)
      VALUES (:productId, :categoryId)
      ON CONFLICT DO NOTHING
      """,
      new MapSqlParameterSource()
        .addValue("productId", productId)
        .addValue("categoryId", categoryId)
    );
  }

  @Override
  @Transactional
  public void removeCategory(Long productId, Long categoryId) {
    productCategories.deleteByProductIdAndCategoryId(productId, categoryId);
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    products.deleteById(id);
    products.flush();
  }

  private CatalogueProductEntity updateExistingEntity(CatalogueProduct product) {
    CatalogueProductEntity entity = products
      .findById(product.id())
      .orElseThrow(() -> CatalogueNotFoundException.product(product.id()));
    entity.setTitle(product.title());
    entity.setSlug(product.slug());
    entity.setFamily(product.family());
    entity.setBrandId(product.brand().id());
    entity.setManufacturerId(product.manufacturer().id());
    entity.setMainImageUrl(product.mainImageUrl());
    entity.setShortDescription(product.shortDescription());
    entity.setActive(product.active());
    return entity;
  }

  private CatalogueProduct toDomain(CatalogueProductEntity product) {
    var brand = brands
      .findById(product.getBrandId())
      .orElseThrow(() -> CatalogueReferenceNotFoundException.brand(product.getBrandId()));
    var manufacturer = manufacturers
      .findById(product.getManufacturerId())
      .orElseThrow(() ->
        CatalogueReferenceNotFoundException.manufacturer(product.getManufacturerId())
      );
    var identifiers = productIdentifiers
      .findByProductIdOrderByPrimaryDescIdAsc(product.getId())
      .stream()
      .map(CataloguePersistenceMapper::toDomain)
      .toList();
    return CataloguePersistenceMapper.toDomain(
      product,
      CataloguePersistenceMapper.toDomain(brand),
      CataloguePersistenceMapper.toDomain(manufacturer),
      identifiers
    );
  }

  private void replaceIdentifiers(Long productId, CatalogueProduct product) {
    productIdentifiers.deleteByProductId(productId);
    productIdentifiers.flush();
    if (product.identifiers().isEmpty()) {
      return;
    }
    productIdentifiers.saveAllAndFlush(
      product
        .identifiers()
        .stream()
        .map(identifier -> CataloguePersistenceMapper.toEntity(productId, identifier))
        .toList()
    );
  }

  private static RuntimeException translateReferenceIntegrityFailure(
    CatalogueProduct product,
    DataIntegrityViolationException exception
  ) {
    String message = exception.getMostSpecificCause().getMessage();
    if (message != null && message.contains("fk_catalogue_product_brand")) {
      return CatalogueReferenceNotFoundException.brand(product.brand().id());
    }
    if (message != null && message.contains("fk_catalogue_product_manufacturer")) {
      return CatalogueReferenceNotFoundException.manufacturer(product.manufacturer().id());
    }
    if (message != null && message.contains("ux_catalogue_product_identifier_gtin14")) {
      String canonical = product.identifiers().isEmpty()
        ? ""
        : product.identifiers().getFirst().canonicalGtin14();
      return new DuplicateProductIdentifierException(canonical);
    }
    return exception;
  }
}
