package com.ferozfaiz.uniapi.catalogue.api.mapper;

import com.ferozfaiz.uniapi.catalogue.api.dto.BrandResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryBreadcrumbItemResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryCapabilityResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryListItemResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryParentOptionResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryParentOptionSliceResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryTreeItemResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateBrandRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateManufacturerRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateProductRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ManufacturerResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.MoveCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchBrandRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchManufacturerRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchProductRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductDetailResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductIdentifierRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductIdentifierResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductListItemResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.ReorderCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateBrandRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateManufacturerRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateProductRequest;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionDisabledReason;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProductIdentifier;
import com.ferozfaiz.uniapi.catalogue.domain.validation.ProductIdentifierRules;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductDetailProjection;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductListItemProjection;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategoryCapabilityMetadata;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageLinks;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.List;

public final class CatalogueApiMapper {

  private CatalogueApiMapper() {}

  public static PageResponse<ProductListItemResponse> toPageResponse(
    PageResult<ProductListItemProjection> page,
    PageLinks links
  ) {
    return PageResponse.from(page, CatalogueApiMapper::toListItemResponse, links);
  }

  public static ProductListItemResponse toListItemResponse(ProductListItemProjection item) {
    return new ProductListItemResponse(
      item.id(),
      item.title(),
      item.slug(),
      canonicalUrl(item.id(), item.slug()),
      item.family(),
      new ProductListItemResponse.SummaryResponse(
        item.brandId(),
        item.brandName(),
        item.brandSlug()
      ),
      new ProductListItemResponse.SummaryResponse(
        item.manufacturerId(),
        item.manufacturerName(),
        item.manufacturerSlug()
      ),
      toIdentifierResponse(item.primaryIdentifier()),
      item.mainImageUrl(),
      item.shortDescription(),
      item.priceAmount() == null
        ? null
        : new ProductListItemResponse.PriceResponse(
            item.priceAmount(),
            item.priceCurrency(),
            item.priceMarketplace(),
            item.priceAvailability()
          ),
      item.merchantStoreId() == null
        ? null
        : new ProductListItemResponse.OfferResponse(
            item.merchantStoreId(),
            item.merchantStoreName(),
            item.merchantStoreSlug()
          ),
      item.capacityGb() == null && item.storageType() == null
        ? null
        : new ProductListItemResponse.StorageSummaryResponse(
            item.storageType(),
            item.capacityGb(),
            item.storageInterfaceType(),
            item.formFactor()
          ),
      item.coreCount() == null && item.socket() == null
        ? null
        : new ProductListItemResponse.CpuSummaryResponse(
            item.socket(),
            item.coreCount(),
            item.threadCount(),
            item.boostClockGhz()
          ),
      item.vramGb() == null && item.gpuChipset() == null
        ? null
        : new ProductListItemResponse.GraphicsCardSummaryResponse(
            item.gpuChipset(),
            item.vramGb(),
            item.memoryType(),
            item.boostClockMhz()
          ),
      item.createdAt(),
      item.updatedAt()
    );
  }

  public static ProductDetailResponse toDetailResponse(ProductDetailProjection detail) {
    ProductListItemProjection product = detail.product();
    return new ProductDetailResponse(
      product.id(),
      product.title(),
      product.slug(),
      canonicalUrl(product.id(), product.slug()),
      product.family(),
      new ProductListItemResponse.SummaryResponse(
        product.brandId(),
        product.brandName(),
        product.brandSlug()
      ),
      new ProductListItemResponse.SummaryResponse(
        product.manufacturerId(),
        product.manufacturerName(),
        product.manufacturerSlug()
      ),
      detail
        .categories()
        .stream()
        .map(category ->
          new ProductListItemResponse.SummaryResponse(
            category.id(),
            category.name(),
            category.slug()
          )
        )
        .toList(),
      detail.identifiers().stream().map(CatalogueApiMapper::toIdentifierResponse).toList(),
      product.mainImageUrl(),
      product.shortDescription(),
      product.priceAmount() == null
        ? null
        : new ProductListItemResponse.PriceResponse(
            product.priceAmount(),
            product.priceCurrency(),
            product.priceMarketplace(),
            product.priceAvailability()
          ),
      product.merchantStoreId() == null
        ? null
        : new ProductListItemResponse.OfferResponse(
            product.merchantStoreId(),
            product.merchantStoreName(),
            product.merchantStoreSlug()
          ),
      product.capacityGb() == null && product.storageType() == null
        ? null
        : new ProductDetailResponse.StorageDetailResponse(
            product.storageType(),
            product.capacityGb(),
            product.storageInterfaceType(),
            product.formFactor(),
            product.readSpeedMbps(),
            product.writeSpeedMbps(),
            detail.storageRpm(),
            detail.storageCacheMb()
          ),
      product.coreCount() == null && product.socket() == null
        ? null
        : new ProductDetailResponse.CpuDetailResponse(
            product.socket(),
            product.coreCount(),
            product.threadCount(),
            detail.baseClockGhz(),
            product.boostClockGhz(),
            detail.cpuArchitecture(),
            detail.integratedGraphics(),
            detail.cpuTdpWatts()
          ),
      product.vramGb() == null && product.gpuChipset() == null
        ? null
        : new ProductDetailResponse.GraphicsCardDetailResponse(
            product.gpuChipset(),
            product.vramGb(),
            product.memoryType(),
            product.boostClockMhz(),
            detail.gpuInterfaceType(),
            detail.gpuLengthMm(),
            detail.gpuTdpWatts()
          ),
      product.createdAt(),
      product.updatedAt()
    );
  }

  public static ProductDetailResponse toDetailResponse(CatalogueProduct product) {
    return new ProductDetailResponse(
      product.id(),
      product.title(),
      product.slug(),
      canonicalUrl(product.id(), product.slug()),
      product.family(),
      new ProductListItemResponse.SummaryResponse(
        product.brand().id(),
        product.brand().name(),
        product.brand().slug()
      ),
      new ProductListItemResponse.SummaryResponse(
        product.manufacturer().id(),
        product.manufacturer().name(),
        product.manufacturer().slug()
      ),
      List.of(),
      product.identifiers().stream().map(CatalogueApiMapper::toIdentifierResponse).toList(),
      product.mainImageUrl(),
      product.shortDescription(),
      null,
      null,
      null,
      null,
      null,
      product.createdAt(),
      product.updatedAt()
    );
  }

  public static UpsertProductCommand toCommand(CreateProductRequest request) {
    return new UpsertProductCommand(
      null,
      request.title(),
      request.slug(),
      request.family(),
      request.brandId(),
      request.manufacturerId(),
      request.mainImageUrl(),
      request.shortDescription(),
      activeOrFalse(request.active()),
      toIdentifiers(request.identifiers())
    );
  }

  public static UpsertProductCommand toCommand(Long id, UpdateProductRequest request) {
    return new UpsertProductCommand(
      id,
      request.title(),
      request.slug(),
      request.family(),
      request.brandId(),
      request.manufacturerId(),
      request.mainImageUrl(),
      request.shortDescription(),
      activeOrFalse(request.active()),
      toIdentifiers(request.identifiers())
    );
  }

  public static UpsertProductCommand toCommand(
    CatalogueProduct existing,
    PatchProductRequest request
  ) {
    return new UpsertProductCommand(
      existing.id(),
      request.titlePresent() ? request.title() : existing.title(),
      request.slugPresent() ? request.slug() : existing.slug(),
      request.familyPresent() ? request.family() : existing.family(),
      request.brandIdPresent() ? request.brandId() : existing.brand().id(),
      request.manufacturerIdPresent() ? request.manufacturerId() : existing.manufacturer().id(),
      request.mainImageUrlPresent() ? request.mainImageUrl() : existing.mainImageUrl(),
      request.shortDescriptionPresent() ? request.shortDescription() : existing.shortDescription(),
      request.activePresent() ? activeOrRejectNull(request.active(), "active") : existing.active(),
      request.identifiersPresent() ? toIdentifiers(request.identifiers()) : existing.identifiers()
    );
  }

  public static CatalogueCategoryResponse toCategoryResponse(CatalogueCategory category) {
    return new CatalogueCategoryResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryResponse toCategoryResponse(
    AdminCategoryModels.Category category
  ) {
    return new CatalogueCategoryResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryListItemResponse toCategoryListItemResponse(
    CatalogueCategoryNode node
  ) {
    CatalogueCategory category = node.category();
    return new CatalogueCategoryListItemResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      node.depth(),
      node.hasChildren(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryListItemResponse toCategoryListItemResponse(
    AdminCategoryModels.CategoryNode node
  ) {
    AdminCategoryModels.Category category = node.category();
    return new CatalogueCategoryListItemResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      node.depth(),
      node.hasChildren(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryBreadcrumbItemResponse toCategoryBreadcrumbItemResponse(
    CatalogueCategoryBreadcrumbItem item
  ) {
    CatalogueCategory category = item.category();
    return new CatalogueCategoryBreadcrumbItemResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      item.depth(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryBreadcrumbItemResponse toCategoryBreadcrumbItemResponse(
    AdminCategoryModels.BreadcrumbItem item
  ) {
    AdminCategoryModels.Category category = item.category();
    return new CatalogueCategoryBreadcrumbItemResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      item.depth(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryParentOptionSliceResponse toCategoryParentOptionSliceResponse(
    CatalogueCategoryParentOptionSlice slice
  ) {
    return new CatalogueCategoryParentOptionSliceResponse(
      slice.data().stream().map(CatalogueApiMapper::toCategoryParentOptionResponse).toList(),
      slice.hasMore(),
      slice.nextCursor()
    );
  }

  public static CatalogueCategoryParentOptionSliceResponse toCategoryParentOptionSliceResponse(
    AdminCategoryModels.ParentOptionSlice slice
  ) {
    return new CatalogueCategoryParentOptionSliceResponse(
      slice.data().stream().map(CatalogueApiMapper::toCategoryParentOptionResponse).toList(),
      slice.hasMore(),
      slice.nextCursor()
    );
  }

  public static CatalogueCategoryParentOptionResponse toCategoryParentOptionResponse(
    CatalogueCategoryParentOption option
  ) {
    CatalogueCategory category = option.category();
    return new CatalogueCategoryParentOptionResponse(
      category.id(),
      category.parentId(),
      category.name(),
      category.slug(),
      category.active(),
      option.depth(),
      option.label(),
      option.path().stream().map(CatalogueApiMapper::toCategoryBreadcrumbItemResponse).toList(),
      option.selectable(),
      option.disabledReason()
    );
  }

  public static CatalogueCategoryParentOptionResponse toCategoryParentOptionResponse(
    AdminCategoryModels.ParentOption option
  ) {
    AdminCategoryModels.Category category = option.category();
    return new CatalogueCategoryParentOptionResponse(
      category.id(),
      category.parentId(),
      category.name(),
      category.slug(),
      category.active(),
      option.depth(),
      option.label(),
      option.path().stream().map(CatalogueApiMapper::toCategoryBreadcrumbItemResponse).toList(),
      option.selectable(),
      toDisabledReason(option.disabledReason())
    );
  }

  public static CatalogueCategoryTreeItemResponse toCategoryTreeItemResponse(
    CatalogueCategoryTreeNode node
  ) {
    return toCategoryTreeItemResponse(node, true);
  }

  public static CatalogueCategoryTreeItemResponse toCategoryTreeItemResponse(
    CatalogueCategoryTreeNode node,
    boolean sortRankExposed
  ) {
    CatalogueCategory category = node.category();
    return new CatalogueCategoryTreeItemResponse(
      category.id(),
      category.parentId(),
      sortRankExposed ? category.sortRank() : null,
      node.depth(),
      node.hasChildren(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CatalogueCategoryTreeItemResponse toCategoryTreeItemResponse(
    AdminCategoryModels.CategoryNode node,
    boolean sortRankExposed
  ) {
    AdminCategoryModels.Category category = node.category();
    return new CatalogueCategoryTreeItemResponse(
      category.id(),
      category.parentId(),
      sortRankExposed ? category.sortRank() : null,
      node.depth(),
      node.hasChildren(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static PageResponse<CatalogueCategoryResponse> toCategoryPageResponse(
    PageResult<CatalogueCategory> page,
    PageLinks links
  ) {
    return PageResponse.from(page, CatalogueApiMapper::toCategoryResponse, links);
  }

  public static PageResponse<CatalogueCategoryListItemResponse> toCategoryListItemPageResponse(
    PageResult<CatalogueCategoryNode> page,
    PageLinks links
  ) {
    return PageResponse.from(page, CatalogueApiMapper::toCategoryListItemResponse, links);
  }

  public static PageResponse<
    CatalogueCategoryListItemResponse
  > toGenericCategoryListItemPageResponse(
    PageResult<AdminCategoryModels.CategoryNode> page,
    PageLinks links
  ) {
    return PageResponse.from(page, node -> toCategoryListItemResponse(node), links);
  }

  public static PageResponse<CatalogueCategoryTreeItemResponse> toCategoryTreeItemPageResponse(
    PageResult<CatalogueCategoryTreeNode> page,
    PageLinks links
  ) {
    return PageResponse.from(page, CatalogueApiMapper::toCategoryTreeItemResponse, links);
  }

  public static PageResponse<CatalogueCategoryTreeItemResponse> toCategoryTreeItemPageResponse(
    PageResult<CatalogueCategoryTreeNode> page,
    PageLinks links,
    boolean sortRankExposed
  ) {
    return PageResponse.from(
      page,
      node -> toCategoryTreeItemResponse(node, sortRankExposed),
      links
    );
  }

  public static PageResponse<
    CatalogueCategoryTreeItemResponse
  > toGenericCategoryTreeItemPageResponse(
    PageResult<AdminCategoryModels.CategoryNode> page,
    PageLinks links,
    boolean sortRankExposed
  ) {
    return PageResponse.from(
      page,
      node -> toCategoryTreeItemResponse(node, sortRankExposed),
      links
    );
  }

  public static CatalogueCategoryCapabilityResponse toCapabilityResponse(
    CategoryCapabilityMetadata metadata
  ) {
    return new CatalogueCategoryCapabilityResponse(
      metadata.rankingEnabled(),
      metadata.placementModes().stream().map(Enum::name).toList(),
      metadata.sortRankExposed(),
      metadata
        .fallbackOrder()
        .stream()
        .map(order ->
          new CatalogueCategoryCapabilityResponse.FallbackOrderResponse(
            order.property(),
            order.direction().name()
          )
        )
        .toList()
    );
  }

  public static UpsertCatalogueCategoryCommand toCommand(CreateCatalogueCategoryRequest request) {
    return new UpsertCatalogueCategoryCommand(
      null,
      request.parentId(),
      request.name(),
      request.slug(),
      activeOrFalse(request.active()),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static AdminCategoryCreateCommand toGenericCommand(
    CreateCatalogueCategoryRequest request
  ) {
    return new AdminCategoryCreateCommand(
      request.parentId(),
      request.name(),
      request.slug(),
      activeOrFalse(request.active()),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static UpsertCatalogueCategoryCommand toCommand(
    Long id,
    UpdateCatalogueCategoryRequest request
  ) {
    return new UpsertCatalogueCategoryCommand(
      id,
      request.parentId(),
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static AdminCategoryUpdateCommand toGenericCommand(
    UpdateCatalogueCategoryRequest request
  ) {
    return new AdminCategoryUpdateCommand(
      request.parentId(),
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static UpsertCatalogueCategoryCommand toCommand(
    CatalogueCategory existing,
    PatchCatalogueCategoryRequest request
  ) {
    return new UpsertCatalogueCategoryCommand(
      existing.id(),
      request.parentIdPresent() ? request.parentId() : existing.parentId(),
      request.namePresent() ? request.name() : existing.name(),
      request.slugPresent() ? request.slug() : existing.slug(),
      request.activePresent() ? activeOrRejectNull(request.active(), "active") : existing.active()
    );
  }

  public static AdminCategoryPatchCommand toGenericCommand(PatchCatalogueCategoryRequest request) {
    return new AdminCategoryPatchCommand(
      request.parentId(),
      request.parentIdPresent(),
      request.name(),
      request.namePresent(),
      request.slug(),
      request.slugPresent(),
      request.active(),
      request.activePresent()
    );
  }

  public static SparseOrderTarget toOrderTarget(MoveCatalogueCategoryRequest request) {
    return toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex());
  }

  public static AdminCategoryMoveCommand toGenericCommand(MoveCatalogueCategoryRequest request) {
    return new AdminCategoryMoveCommand(
      request.newParentId(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static ReorderCatalogueCategoryCommand toCommand(ReorderCatalogueCategoryRequest request) {
    return new ReorderCatalogueCategoryCommand(
      request.parentId(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static AdminCategoryReorderCommand toGenericCommand(
    ReorderCatalogueCategoryRequest request
  ) {
    return new AdminCategoryReorderCommand(
      request.parentId(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static BrandResponse toBrandResponse(CatalogueBrand brand) {
    return new BrandResponse(
      brand.id(),
      brand.name(),
      brand.slug(),
      brand.active(),
      brand.createdAt(),
      brand.updatedAt()
    );
  }

  public static ManufacturerResponse toManufacturerResponse(CatalogueManufacturer manufacturer) {
    return new ManufacturerResponse(
      manufacturer.id(),
      manufacturer.name(),
      manufacturer.slug(),
      manufacturer.active(),
      manufacturer.createdAt(),
      manufacturer.updatedAt()
    );
  }

  public static UpsertBrandCommand toCommand(CreateBrandRequest request) {
    return new UpsertBrandCommand(
      null,
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static UpsertBrandCommand toCommand(Long id, UpdateBrandRequest request) {
    return new UpsertBrandCommand(
      id,
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static UpsertBrandCommand toCommand(CatalogueBrand existing, PatchBrandRequest request) {
    return new UpsertBrandCommand(
      existing.id(),
      request.namePresent() ? request.name() : existing.name(),
      request.slugPresent() ? request.slug() : existing.slug(),
      request.activePresent() ? activeOrRejectNull(request.active(), "active") : existing.active()
    );
  }

  public static UpsertManufacturerCommand toCommand(CreateManufacturerRequest request) {
    return new UpsertManufacturerCommand(
      null,
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static UpsertManufacturerCommand toCommand(Long id, UpdateManufacturerRequest request) {
    return new UpsertManufacturerCommand(
      id,
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static UpsertManufacturerCommand toCommand(
    CatalogueManufacturer existing,
    PatchManufacturerRequest request
  ) {
    return new UpsertManufacturerCommand(
      existing.id(),
      request.namePresent() ? request.name() : existing.name(),
      request.slugPresent() ? request.slug() : existing.slug(),
      request.activePresent() ? activeOrRejectNull(request.active(), "active") : existing.active()
    );
  }

  private static CatalogueCategoryParentOptionDisabledReason toDisabledReason(String reason) {
    return reason == null ? null : CatalogueCategoryParentOptionDisabledReason.valueOf(reason);
  }

  private static boolean activeOrFalse(Boolean active) {
    return Boolean.TRUE.equals(active);
  }

  private static List<CatalogueProductIdentifier> toIdentifiers(
    List<ProductIdentifierRequest> identifiers
  ) {
    if (identifiers == null || identifiers.isEmpty()) {
      return List.of();
    }
    return identifiers
      .stream()
      .map(identifier ->
        ProductIdentifierRules.normalize(
          identifier.family(),
          identifier.value(),
          Boolean.TRUE.equals(identifier.primary())
        )
      )
      .toList();
  }

  private static ProductIdentifierResponse toIdentifierResponse(
    CatalogueProductIdentifier identifier
  ) {
    if (identifier == null) {
      return null;
    }
    return new ProductIdentifierResponse(
      identifier.family().apiValue(),
      identifier.value(),
      identifier.canonicalGtin14(),
      identifier.primary()
    );
  }

  private static boolean activeOrRejectNull(Boolean active, String fieldName) {
    if (active == null) {
      throw new InvalidRequestException(
        "catalogue.api.patch.field.required",
        fieldName + " cannot be null",
        fieldName
      );
    }
    return active;
  }

  private static SparseOrderTarget toOrderTarget(Long beforeId, Long afterId, Integer targetIndex) {
    int explicitFields =
      (beforeId == null ? 0 : 1) + (afterId == null ? 0 : 1) + (targetIndex == null ? 0 : 1);
    if (explicitFields > 1) {
      throw new InvalidRequestException(
        "catalogue.api.category.placement.conflict",
        "specify only one placement field"
      );
    }
    if (targetIndex != null && targetIndex < 0) {
      throw new InvalidRequestException(
        "catalogue.api.category.placement.target-index.bounds",
        "targetIndex must be zero or greater",
        0
      );
    }
    return SparseOrderTarget.from(beforeId, afterId, targetIndex);
  }

  private static String canonicalUrl(Long id, String slug) {
    return "/api/v1/products/" + id + "/" + slug;
  }
}
