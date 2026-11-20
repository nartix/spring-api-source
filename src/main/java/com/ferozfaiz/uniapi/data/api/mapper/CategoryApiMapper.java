package com.ferozfaiz.uniapi.data.api.mapper;

import com.ferozfaiz.uniapi.data.api.dto.CategoryCapabilityResponse;
import com.ferozfaiz.uniapi.data.api.dto.CategoryListItemResponse;
import com.ferozfaiz.uniapi.data.api.dto.CategoryMoveEstimateResponse;
import com.ferozfaiz.uniapi.data.api.dto.CategoryResponse;
import com.ferozfaiz.uniapi.data.api.dto.CategoryTreeResponse;
import com.ferozfaiz.uniapi.data.api.dto.CreateCategoryRequest;
import com.ferozfaiz.uniapi.data.api.dto.ListCategoriesRequest;
import com.ferozfaiz.uniapi.data.api.dto.MoveCategoryRequest;
import com.ferozfaiz.uniapi.data.api.dto.PatchCategoryRequest;
import com.ferozfaiz.uniapi.data.api.dto.ReorderCategoryRequest;
import com.ferozfaiz.uniapi.data.api.dto.UpdateCategoryRequest;
import com.ferozfaiz.uniapi.data.application.command.CreateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.MoveCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.PatchCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.ReorderCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.UpdateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.query.CategoryTree;
import com.ferozfaiz.uniapi.data.application.query.ListCategoriesQuery;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategoryCapabilityMetadata;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageLinks;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;

public final class CategoryApiMapper {

  private CategoryApiMapper() {}

  public static CreateCategoryCommand toCommand(CreateCategoryRequest request) {
    return new CreateCategoryCommand(
      request.parentId(),
      request.slug(),
      request.name(),
      request.description(),
      request.active(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static UpdateCategoryCommand toCommand(UpdateCategoryRequest request) {
    return new UpdateCategoryCommand(
      request.slug(),
      request.name(),
      request.description(),
      request.active()
    );
  }

  public static PatchCategoryCommand toCommand(PatchCategoryRequest request) {
    return new PatchCategoryCommand(
      request.slug(),
      request.name(),
      request.description(),
      request.active()
    );
  }

  public static MoveCategoryCommand toCommand(MoveCategoryRequest request) {
    return new MoveCategoryCommand(
      request.parentId(),
      request.effectiveMode(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static ReorderCategoryCommand toCommand(ReorderCategoryRequest request) {
    return new ReorderCategoryCommand(
      request.parentId(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static ListCategoriesQuery toQuery(ListCategoriesRequest request) {
    return new ListCategoriesQuery(request.toPageCriteria(), request.toSortCriteria());
  }

  public static CategoryResponse toResponse(Category category) {
    return new CategoryResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      category.slug(),
      category.name(),
      category.description(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static CategoryListItemResponse toListItemResponse(CategoryNode node) {
    Category category = node.category();
    return new CategoryListItemResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      node.depth(),
      category.slug(),
      category.name(),
      category.description(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  public static PageResponse<CategoryListItemResponse> toPageResponse(
    PageResult<CategoryNode> page,
    PageLinks links
  ) {
    return PageResponse.from(page, CategoryApiMapper::toListItemResponse, links);
  }

  public static CategoryCapabilityResponse toCapabilityResponse(
    CategoryCapabilityMetadata metadata
  ) {
    return new CategoryCapabilityResponse(
      metadata.rankingEnabled(),
      metadata.placementModes().stream().map(Enum::name).toList(),
      metadata.sortRankExposed(),
      metadata
        .fallbackOrder()
        .stream()
        .map(order ->
          new CategoryCapabilityResponse.FallbackOrderResponse(
            order.property(),
            order.direction().name()
          )
        )
        .toList()
    );
  }

  public static CategoryTreeResponse toTreeResponse(CategoryTree tree) {
    Category category = tree.category();
    return new CategoryTreeResponse(
      category.id(),
      category.parentId(),
      category.sortRank(),
      category.slug(),
      category.name(),
      category.description(),
      category.active(),
      tree.children().stream().map(CategoryApiMapper::toTreeResponse).toList()
    );
  }

  public static CategoryMoveEstimateResponse toResponse(CategoryMoveEstimate estimate) {
    return new CategoryMoveEstimateResponse(
      estimate.subtreeSize(),
      estimate.oldAncestorCount(),
      estimate.newAncestorCount(),
      estimate.estimatedDeleteRows(),
      estimate.estimatedInsertRows(),
      estimate.estimatedTotalRows(),
      estimate.subtreeMaxDepth(),
      estimate.newParentDepth(),
      estimate.resultingMaxDepth(),
      estimate.parentChangeRequired()
    );
  }

  private static SparseOrderTarget toOrderTarget(Long beforeId, Long afterId, Integer targetIndex) {
    int explicitFields =
      (beforeId == null ? 0 : 1) + (afterId == null ? 0 : 1) + (targetIndex == null ? 0 : 1);
    if (explicitFields > 1) {
      throw new InvalidRequestException(
        "data.api.category.placement.conflict",
        "specify only one placement field"
      );
    }
    if (targetIndex != null && targetIndex < 0) {
      throw new InvalidRequestException(
        "data.api.category.placement.target-index.bounds",
        "targetIndex must be zero or greater",
        0
      );
    }
    return SparseOrderTarget.from(beforeId, afterId, targetIndex);
  }
}
