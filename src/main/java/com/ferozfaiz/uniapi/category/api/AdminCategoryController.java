package com.ferozfaiz.uniapi.category.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryActivate;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryBreadcrumb;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryCapabilities;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryCreate;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryDeactivate;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryDelete;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryGet;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryList;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryMove;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryParentOptions;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryPatch;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryReorder;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryReplace;
import com.ferozfaiz.uniapi.category.api.authorization.RequireAdminCategoryTree;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryBreadcrumbItemResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCreateRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryDetailResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryListItemResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryListRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryMoveRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryParentOptionSliceResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryParentOptionsRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryPatchRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryReorderRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryTreeItemResponse;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryTreeRequest;
import com.ferozfaiz.uniapi.category.api.dto.AdminCategoryUpdateRequest;
import com.ferozfaiz.uniapi.category.api.mapper.AdminCategoryApiMapper;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.shared.pagination.PageLinksAssembler;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("${spring.data.rest.base-path}/admin/category-systems/{systemKey}/categories")
public class AdminCategoryController {

  private final AdminCategoryApplicationService categories;

  public AdminCategoryController(AdminCategoryApplicationService categories) {
    this.categories = categories;
  }

  @GetMapping("/capabilities")
  @RequireAdminCategoryCapabilities
  @Operation(
    summary = "Get admin category capabilities for a category system",
    description = "Returns descriptor-derived ranking, placement mode, sort-rank exposure, and fallback-order capabilities for the selected category system."
  )
  public AdminCategoryCapabilityResponse capabilities(@PathVariable String systemKey) {
    return AdminCategoryApiMapper.toCapabilityResponse(categories.capabilities(systemKey));
  }

  @GetMapping
  @RequireAdminCategoryList
  @Operation(
    summary = "List admin categories for a category system",
    description = "Returns a pageable selected-system category list with parent scope, filters, sorting, and generic category list item responses."
  )
  public PageResponse<AdminCategoryListItemResponse> listCategories(
    @PathVariable String systemKey,
    @ParameterObject @Valid @ModelAttribute AdminCategoryListRequest request
  ) {
    var definition = categories.definition(systemKey, AdminCategorySystemDefinition.Operation.LIST);
    var page = categories.list(
      systemKey,
      AdminCategoryApiMapper.toValidationInput(definition, request)
    );
    return AdminCategoryApiMapper.toListItemPageResponse(
      page,
      PageLinksAssembler.fromCurrentRequest(page)
    );
  }

  @GetMapping("/tree")
  @RequireAdminCategoryTree
  @Operation(
    summary = "List admin categories as a bounded selected-system tree",
    description = "Returns the selected-system tree when the total node count is within the configured generic category tree policy."
  )
  public PageResponse<AdminCategoryTreeItemResponse> listCategoryTree(
    @PathVariable String systemKey,
    @ParameterObject @Valid @ModelAttribute AdminCategoryTreeRequest request
  ) {
    var definition = categories.definition(systemKey, AdminCategorySystemDefinition.Operation.TREE);
    var page = categories.tree(
      systemKey,
      AdminCategoryApiMapper.toValidationInput(definition, request)
    );
    return AdminCategoryApiMapper.toTreeItemPageResponse(
      page,
      PageLinksAssembler.fromCurrentRequest(page)
    );
  }

  @GetMapping("/parent-options")
  @RequireAdminCategoryParentOptions
  @Operation(
    summary = "List admin category parent options for a category system",
    description = "Returns a bounded cursor slice of selected-system parent candidates for create, edit, and move flows."
  )
  public AdminCategoryParentOptionSliceResponse listParentOptions(
    @PathVariable String systemKey,
    @ParameterObject @Valid @ModelAttribute AdminCategoryParentOptionsRequest request
  ) {
    return AdminCategoryApiMapper.toParentOptionSliceResponse(
      categories.parentOptions(
        systemKey,
        AdminCategoryApiMapper.toValidationInput(
          categories.definition(systemKey, AdminCategorySystemDefinition.Operation.PARENT_OPTIONS),
          request
        )
      )
    );
  }

  @GetMapping("/{id}")
  @RequireAdminCategoryGet
  @Operation(summary = "Get one admin category for a category system")
  public AdminCategoryDetailResponse getCategory(
    @PathVariable String systemKey,
    @PathVariable Long id
  ) {
    return AdminCategoryApiMapper.toDetailResponse(categories.get(systemKey, id));
  }

  @GetMapping("/{id}/breadcrumb")
  @RequireAdminCategoryBreadcrumb
  @Operation(
    summary = "Get an admin category breadcrumb for a category system",
    description = "Returns breadcrumb items ordered from root category to the selected category."
  )
  public List<AdminCategoryBreadcrumbItemResponse> getCategoryBreadcrumb(
    @PathVariable String systemKey,
    @PathVariable Long id
  ) {
    return categories
      .breadcrumb(systemKey, id)
      .stream()
      .map(AdminCategoryApiMapper::toBreadcrumbItemResponse)
      .toList();
  }

  @PostMapping
  @RequireAdminCategoryCreate
  @Operation(
    summary = "Create an admin category for a category system",
    description = "Creates a selected-system category. Ranked systems accept optional beforeId, afterId, or zero-based targetIndex placement fields."
  )
  public ResponseEntity<AdminCategoryDetailResponse> createCategory(
    @PathVariable String systemKey,
    @Valid @RequestBody AdminCategoryCreateRequest request
  ) {
    var category = categories.create(
      systemKey,
      AdminCategoryApiMapper.toValidationInput(
        categories.definition(systemKey, AdminCategorySystemDefinition.Operation.CREATE),
        request
      )
    );
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(category.id())
      .toUri();
    return ResponseEntity.created(location).body(AdminCategoryApiMapper.toDetailResponse(category));
  }

  @PutMapping("/{id}")
  @RequireAdminCategoryReplace
  @Operation(summary = "Replace an admin category for a category system")
  public AdminCategoryDetailResponse updateCategory(
    @PathVariable String systemKey,
    @PathVariable Long id,
    @Valid @RequestBody AdminCategoryUpdateRequest request
  ) {
    return AdminCategoryApiMapper.toDetailResponse(
      categories.update(
        systemKey,
        id,
        AdminCategoryApiMapper.toValidationInput(
          categories.definition(systemKey, AdminCategorySystemDefinition.Operation.REPLACE),
          id,
          request
        )
      )
    );
  }

  @PatchMapping(
    value = "/{id}",
    consumes = { MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json" }
  )
  @RequireAdminCategoryPatch
  @Operation(summary = "Partially update an admin category for a category system")
  public AdminCategoryDetailResponse patchCategory(
    @PathVariable String systemKey,
    @PathVariable Long id,
    @RequestBody ObjectNode patch
  ) {
    return AdminCategoryApiMapper.toDetailResponse(
      categories.patch(
        systemKey,
        id,
        AdminCategoryApiMapper.toValidationInput(
          categories.definition(systemKey, AdminCategorySystemDefinition.Operation.PATCH),
          id,
          AdminCategoryPatchRequest.from(patch)
        )
      )
    );
  }

  @DeleteMapping("/{id}")
  @RequireAdminCategoryDelete
  @Operation(summary = "Delete an admin category for a category system")
  public ResponseEntity<Void> deleteCategory(
    @PathVariable String systemKey,
    @PathVariable Long id
  ) {
    categories.delete(systemKey, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/move")
  @RequireAdminCategoryMove
  @Operation(
    summary = "Move an admin category for a category system",
    description = "Moves a selected-system category to a destination parent. Ranked systems accept optional beforeId, afterId, or zero-based targetIndex placement fields."
  )
  public ResponseEntity<Void> moveCategory(
    @PathVariable String systemKey,
    @PathVariable Long id,
    @Valid @RequestBody AdminCategoryMoveRequest request
  ) {
    categories.move(
      systemKey,
      id,
      AdminCategoryApiMapper.toValidationInput(
        categories.definition(systemKey, AdminCategorySystemDefinition.Operation.MOVE),
        AdminCategorySystemDefinition.Operation.MOVE,
        request
      )
    );
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reorder")
  @RequireAdminCategoryReorder
  @Operation(
    summary = "Reorder an admin category for a category system",
    description = "Reorders a selected-system category within a sibling group by append, beforeId, afterId, or zero-based targetIndex."
  )
  public AdminCategoryDetailResponse reorderCategory(
    @PathVariable String systemKey,
    @PathVariable Long id,
    @Valid @RequestBody AdminCategoryReorderRequest request
  ) {
    return AdminCategoryApiMapper.toDetailResponse(
      categories.reorder(
        systemKey,
        id,
        AdminCategoryApiMapper.toValidationInput(
          categories.definition(systemKey, AdminCategorySystemDefinition.Operation.REORDER),
          AdminCategorySystemDefinition.Operation.REORDER,
          request
        )
      )
    );
  }

  @PostMapping("/{id}/activate")
  @RequireAdminCategoryActivate
  @Operation(summary = "Activate an admin category for a category system")
  public ResponseEntity<Void> activateCategory(
    @PathVariable String systemKey,
    @PathVariable Long id
  ) {
    categories.activate(systemKey, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/deactivate")
  @RequireAdminCategoryDeactivate
  @Operation(summary = "Deactivate an admin category for a category system")
  public ResponseEntity<Void> deactivateCategory(
    @PathVariable String systemKey,
    @PathVariable Long id
  ) {
    categories.deactivate(systemKey, id);
    return ResponseEntity.noContent().build();
  }
}
