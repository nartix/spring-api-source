package com.ferozfaiz.uniapi.category.api.mapper;

import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryBreadcrumbItemResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse.AdminCategoryHierarchyCapabilityResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse.AdminCategoryLifecycleCapabilityResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse.AdminCategoryOperationalLimitsResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse.AdminCategoryOrderingCapabilityResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse.FallbackOrderResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCreateRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryDetailResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryListItemResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryListRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryMoveRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryParentOptionResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryParentOptionSliceResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryParentOptionsRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryPatchRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryReorderRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryTreeItemResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryTreeRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryUpdateRequest;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageLinks;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class AdminCategoryApiMapper {

  private AdminCategoryApiMapper() {}

  public static AdminCategoryListItemResponse toListItemResponse(CategoryNode node) {
    var category = node.category();
    return new AdminCategoryListItemResponse(
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

  public static PageResponse<AdminCategoryListItemResponse> toListItemPageResponse(
    PageResult<CategoryNode> page,
    PageLinks links
  ) {
    return PageResponse.from(page, AdminCategoryApiMapper::toListItemResponse, links);
  }

  public static AdminCategoryCapabilityResponse toCapabilityResponse(
    AdminCategorySystemDefinition definition
  ) {
    var ordering = definition.ordering();
    List<String> placementModes = enumNames(
      AdminCategorySystemDefinition.PlacementMode.values(),
      ordering.placementModes()
    );
    List<FallbackOrderResponse> fallbackOrder = definition
      .ordering()
      .fallbackOrder()
      .stream()
      .map(order ->
        new FallbackOrderResponse(publicProperty(order.field()), order.direction().name())
      )
      .toList();
    var hierarchy = definition.hierarchy();
    var lifecycle = definition.lifecycle();
    var limits = definition.operationalLimits();
    return new AdminCategoryCapabilityResponse(
      ordering.rankingEnabled(),
      placementModes,
      ordering.rankExposed(),
      fallbackOrder,
      enumCodes(Operation.values(), definition.operations().supportedOperations()),
      new AdminCategoryHierarchyCapabilityResponse(
        hierarchy.enabled(),
        enumCodes(
          new Operation[] { Operation.TREE, Operation.PARENT_OPTIONS, Operation.BREADCRUMB },
          hierarchy.supportedOperations()
        ),
        enumCodes(new Operation[] { Operation.MOVE }, hierarchy.supportedOperations()),
        hierarchy.rootModel().map(Enum::name).orElse(null)
      ),
      new AdminCategoryOperationalLimitsResponse(
        limits.pageSize().orElse(null),
        limits.searchInputLength().orElse(null),
        limits.filterSelections().orElse(null),
        limits.sortSelections().orElse(null),
        limits.treeSize().orElse(null),
        limits.parentOptionPageSize().orElse(null),
        limits.includeIdCount().orElse(null),
        limits.hierarchyDepth().orElse(null)
      ),
      new AdminCategoryLifecycleCapabilityResponse(
        lifecycle.enabled(),
        enumNames(AdminCategorySystemDefinition.LifecycleState.values(), lifecycle.states()),
        enumCodes(new Operation[] { Operation.ACTIVATE, Operation.DEACTIVATE }, lifecycle.actions())
      ),
      definition.identifierProfile().name(),
      enumCodes(LogicalField.values(), definition.fieldsAndQueries().fields()),
      enumCodes(LogicalField.values(), definition.fieldsAndQueries().searchableFields()),
      enumCodes(LogicalField.values(), definition.fieldsAndQueries().allowedFilters()),
      enumCodes(LogicalField.values(), definition.fieldsAndQueries().allowedSorts()),
      new AdminCategoryOrderingCapabilityResponse(
        ordering.orderingEnabled(),
        ordering.rankingEnabled(),
        ordering.rankExposed(),
        ordering.reorderSupported(),
        placementModes,
        fallbackOrder
      )
    );
  }

  private static <E extends Enum<E>> List<String> enumNames(E[] order, Set<E> selected) {
    return Arrays.stream(order).filter(selected::contains).map(Enum::name).toList();
  }

  private static List<String> enumCodes(Operation[] order, Set<Operation> selected) {
    return Arrays.stream(order).filter(selected::contains).map(Operation::code).toList();
  }

  private static List<String> enumCodes(LogicalField[] order, Set<LogicalField> selected) {
    return Arrays.stream(order).filter(selected::contains).map(LogicalField::code).toList();
  }

  private static String publicProperty(LogicalField field) {
    return switch (field) {
      case IDENTIFIER -> "id";
      case PARENT -> "parentId";
      case SLUG -> "slug";
      case NAME -> "name";
      case RANK -> "sortRank";
      case ACTIVE -> "active";
      case CREATED_AT -> "createdAt";
      case UPDATED_AT -> "updatedAt";
      case HIERARCHY_CONTEXT -> throw new IllegalArgumentException(
        "hierarchy context is not a public sort property"
      );
    };
  }

  public static AdminCategoryTreeItemResponse toTreeItemResponse(CategoryNode node) {
    var category = node.category();
    return new AdminCategoryTreeItemResponse(
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

  public static PageResponse<AdminCategoryTreeItemResponse> toTreeItemPageResponse(
    PageResult<CategoryNode> page,
    PageLinks links
  ) {
    return PageResponse.from(page, AdminCategoryApiMapper::toTreeItemResponse, links);
  }

  public static AdminCategoryDetailResponse toDetailResponse(
    AdminCategoryModels.Category category
  ) {
    return new AdminCategoryDetailResponse(
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

  public static AdminCategoryBreadcrumbItemResponse toBreadcrumbItemResponse(
    AdminCategoryModels.BreadcrumbItem item
  ) {
    var category = item.category();
    return new AdminCategoryBreadcrumbItemResponse(
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

  public static AdminCategoryParentOptionSliceResponse toParentOptionSliceResponse(
    AdminCategoryModels.ParentOptionSlice slice
  ) {
    return new AdminCategoryParentOptionSliceResponse(
      slice.data().stream().map(AdminCategoryApiMapper::toParentOptionResponse).toList(),
      slice.hasMore(),
      slice.nextCursor()
    );
  }

  public static AdminCategoryListValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    AdminCategoryListRequest request
  ) {
    return new AdminCategoryListValidationInput(
      definition,
      request.page(),
      request.size(),
      request.parentId(),
      request.active(),
      request.q(),
      request.slug(),
      request.sort(),
      request.direction()
    );
  }

  public static AdminCategoryTreeValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    AdminCategoryTreeRequest request
  ) {
    return new AdminCategoryTreeValidationInput(
      definition,
      request.page(),
      request.size(),
      request.sort(),
      request.direction()
    );
  }

  public static AdminCategoryParentOptionsValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    AdminCategoryParentOptionsRequest request
  ) {
    return new AdminCategoryParentOptionsValidationInput(
      definition,
      request.q(),
      request.limit(),
      request.cursor(),
      request.includeIds(),
      request.excludeSubtreeId(),
      request.includeInactive()
    );
  }

  public static AdminCategoryCreateValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    AdminCategoryCreateRequest request
  ) {
    return new AdminCategoryCreateValidationInput(
      definition,
      request.parentId(),
      request.beforeId(),
      request.afterId(),
      request.targetIndex(),
      request.name(),
      request.slug(),
      request.active()
    );
  }

  public static AdminCategoryReplaceValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    Long id,
    AdminCategoryUpdateRequest request
  ) {
    return new AdminCategoryReplaceValidationInput(
      definition,
      id,
      request.parentId(),
      request.name(),
      request.slug(),
      request.active()
    );
  }

  public static AdminCategoryPatchValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    Long id,
    AdminCategoryPatchRequest request
  ) {
    return new AdminCategoryPatchValidationInput(
      definition,
      id,
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

  public static AdminCategoryPlacementValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    Operation operation,
    AdminCategoryMoveRequest request
  ) {
    return new AdminCategoryPlacementValidationInput(
      definition,
      operation,
      request.newParentId(),
      request.beforeId(),
      request.afterId(),
      request.targetIndex()
    );
  }

  public static AdminCategoryPlacementValidationInput toValidationInput(
    AdminCategorySystemDefinition definition,
    Operation operation,
    AdminCategoryReorderRequest request
  ) {
    return new AdminCategoryPlacementValidationInput(
      definition,
      operation,
      request.parentId(),
      request.beforeId(),
      request.afterId(),
      request.targetIndex()
    );
  }

  public static AdminCategoryCreateCommand toCommand(AdminCategoryCreateRequest request) {
    return new AdminCategoryCreateCommand(
      request.parentId(),
      request.name(),
      request.slug(),
      activeOrFalse(request.active()),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static AdminCategoryUpdateCommand toCommand(AdminCategoryUpdateRequest request) {
    return new AdminCategoryUpdateCommand(
      request.parentId(),
      request.name(),
      request.slug(),
      activeOrFalse(request.active())
    );
  }

  public static AdminCategoryPatchCommand toCommand(AdminCategoryPatchRequest request) {
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

  public static AdminCategoryMoveCommand toCommand(AdminCategoryMoveRequest request) {
    return new AdminCategoryMoveCommand(
      request.newParentId(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  public static AdminCategoryReorderCommand toCommand(AdminCategoryReorderRequest request) {
    return new AdminCategoryReorderCommand(
      request.parentId(),
      toOrderTarget(request.beforeId(), request.afterId(), request.targetIndex())
    );
  }

  private static AdminCategoryParentOptionResponse toParentOptionResponse(
    AdminCategoryModels.ParentOption option
  ) {
    var category = option.category();
    return new AdminCategoryParentOptionResponse(
      category.id(),
      category.parentId(),
      category.name(),
      category.slug(),
      category.active(),
      option.depth(),
      option.label(),
      option.path().stream().map(AdminCategoryApiMapper::toBreadcrumbItemResponse).toList(),
      option.selectable(),
      option.disabledReason()
    );
  }

  private static boolean activeOrFalse(Boolean active) {
    return Boolean.TRUE.equals(active);
  }

  private static SparseOrderTarget toOrderTarget(Long beforeId, Long afterId, Integer targetIndex) {
    int explicitFields =
      (beforeId == null ? 0 : 1) + (afterId == null ? 0 : 1) + (targetIndex == null ? 0 : 1);
    if (explicitFields > 1) {
      throw new InvalidRequestException(
        "category.api.category.placement.conflict",
        "specify only one placement field"
      );
    }
    if (targetIndex != null && targetIndex < 0) {
      throw new InvalidRequestException(
        "category.api.category.placement.target-index.bounds",
        "targetIndex must be zero or greater",
        0
      );
    }
    return SparseOrderTarget.from(beforeId, afterId, targetIndex);
  }
}
