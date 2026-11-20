package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.mapper;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantProductOffer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueMerchantStore;
import com.ferozfaiz.uniapi.catalogue.domain.model.CataloguePrice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueBrandEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCategoryEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueManufacturerEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueMerchantProductOfferEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueMerchantStoreEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductCurrentPriceEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueProductIdentifierEntity;
import java.util.List;

public final class CataloguePersistenceMapper {

  private CataloguePersistenceMapper() {}

  public static CatalogueProduct toDomain(
    CatalogueProductEntity product,
    CatalogueBrand brand,
    CatalogueManufacturer manufacturer
  ) {
    return toDomain(product, brand, manufacturer, List.of());
  }

  public static CatalogueProduct toDomain(
    CatalogueProductEntity product,
    CatalogueBrand brand,
    CatalogueManufacturer manufacturer,
    List<CatalogueProductIdentifier> identifiers
  ) {
    return new CatalogueProduct(
      product.getId(),
      product.getTitle(),
      product.getSlug(),
      product.getFamily(),
      brand,
      manufacturer,
      product.getMainImageUrl(),
      product.getShortDescription(),
      product.isActive(),
      identifiers,
      product.getCreatedAt(),
      product.getUpdatedAt()
    );
  }

  public static CatalogueProduct toDomain(
    CatalogueProductEntity product,
    CatalogueBrandEntity brand,
    CatalogueManufacturerEntity manufacturer
  ) {
    return toDomain(product, toDomain(brand), toDomain(manufacturer));
  }

  public static CatalogueProductEntity toEntity(CatalogueProduct product) {
    return CatalogueProductEntity.builder()
      .id(product.id())
      .title(product.title())
      .slug(product.slug())
      .family(product.family())
      .brandId(product.brand().id())
      .manufacturerId(product.manufacturer().id())
      .mainImageUrl(product.mainImageUrl())
      .shortDescription(product.shortDescription())
      .active(product.active())
      .createdAt(product.createdAt())
      .updatedAt(product.updatedAt())
      .build();
  }

  public static CatalogueProductIdentifier toDomain(CatalogueProductIdentifierEntity entity) {
    return new CatalogueProductIdentifier(
      entity.getFamily(),
      entity.getValue(),
      entity.getCanonicalGtin14(),
      entity.isPrimary()
    );
  }

  public static CatalogueProductIdentifierEntity toEntity(
    Long productId,
    CatalogueProductIdentifier identifier
  ) {
    return CatalogueProductIdentifierEntity.builder()
      .productId(productId)
      .family(identifier.family())
      .value(identifier.value())
      .canonicalGtin14(identifier.canonicalGtin14())
      .primary(identifier.primary())
      .build();
  }

  public static CatalogueBrand toDomain(CatalogueBrandEntity entity) {
    return new CatalogueBrand(
      entity.getId(),
      entity.getName(),
      entity.getSlug(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }

  public static CatalogueBrandEntity toEntity(CatalogueBrand brand) {
    return CatalogueBrandEntity.builder()
      .id(brand.id())
      .name(brand.name())
      .slug(brand.slug())
      .active(brand.active())
      .createdAt(brand.createdAt())
      .updatedAt(brand.updatedAt())
      .build();
  }

  public static CatalogueManufacturer toDomain(CatalogueManufacturerEntity entity) {
    return new CatalogueManufacturer(
      entity.getId(),
      entity.getName(),
      entity.getSlug(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }

  public static CatalogueManufacturerEntity toEntity(CatalogueManufacturer manufacturer) {
    return CatalogueManufacturerEntity.builder()
      .id(manufacturer.id())
      .name(manufacturer.name())
      .slug(manufacturer.slug())
      .active(manufacturer.active())
      .createdAt(manufacturer.createdAt())
      .updatedAt(manufacturer.updatedAt())
      .build();
  }

  public static CatalogueCategory toDomain(CatalogueCategoryEntity entity) {
    return new CatalogueCategory(
      entity.getId(),
      entity.getParentId(),
      entity.getSortRank(),
      entity.getName(),
      entity.getSlug(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }

  public static CatalogueCategoryEntity toEntity(CatalogueCategory category) {
    return CatalogueCategoryEntity.builder()
      .id(category.id())
      .parentId(category.parentId())
      .sortRank(category.sortRank())
      .name(category.name())
      .slug(category.slug())
      .active(category.active())
      .createdAt(category.createdAt())
      .updatedAt(category.updatedAt())
      .build();
  }

  public static CatalogueMerchantStore toDomain(CatalogueMerchantStoreEntity entity) {
    return new CatalogueMerchantStore(
      entity.getId(),
      entity.getName(),
      entity.getSlug(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }

  public static CatalogueMerchantProductOffer toDomain(CatalogueMerchantProductOfferEntity entity) {
    return new CatalogueMerchantProductOffer(
      entity.getId(),
      entity.getProductId(),
      entity.getMerchantStoreId(),
      entity.getMerchantSku(),
      entity.getMerchantProductCode(),
      entity.getOfferUrl(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }

  public static CataloguePrice toDomain(CatalogueProductCurrentPriceEntity entity) {
    return new CataloguePrice(
      entity.getId(),
      entity.getMerchantProductOfferId(),
      entity.getProductId(),
      entity.getMerchantStoreId(),
      entity.getMarketplace(),
      entity.getCurrency(),
      entity.getAmount(),
      entity.getAvailability(),
      entity.getSource(),
      entity.getCapturedAt(),
      entity.getUpdatedAt()
    );
  }
}
