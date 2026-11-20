package com.ferozfaiz.uniapi.data.api;

import com.ferozfaiz.uniapi.data.api.authorization.RequireCategoriesRead;
import com.ferozfaiz.uniapi.data.api.authorization.RequireCategoriesWrite;
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
import com.ferozfaiz.uniapi.data.api.mapper.CategoryApiMapper;
import com.ferozfaiz.uniapi.data.application.CategoryApplicationService;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategoryCapabilityMetadata;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.shared.error.ApiErrorResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageLinksAssembler;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("${spring.data.rest.base-path}/categories")
public class CategoryController {

  private static final String CATEGORY_SYSTEM = "data";

  private final CategoryApplicationService categories;
  private final CategorySystemRegistry categorySystems;

  public CategoryController(
    CategoryApplicationService categories,
    CategorySystemRegistry categorySystems
  ) {
    this.categories = categories;
    this.categorySystems = categorySystems;
  }

  @GetMapping("/capabilities")
  @RequireCategoriesRead
  @Operation(
    summary = "Get category ranking capabilities",
    description = "Describes whether data categories support sparse ranking, which placement modes clients may send, and whether responses expose sortRank metadata."
  )
  public CategoryCapabilityResponse capabilities() {
    return CategoryApiMapper.toCapabilityResponse(
      CategoryCapabilityMetadata.from(categorySystems.requireDescriptor(CATEGORY_SYSTEM))
    );
  }

  @PostMapping
  @RequireCategoriesWrite
  @Operation(
    summary = "Create category",
    description = "Creates a data category. Ranked systems accept optional beforeId, afterId, or zero-based targetIndex placement fields and return sortRank metadata."
  )
  public ResponseEntity<CategoryResponse> createCategory(
    @Valid @RequestBody CreateCategoryRequest request
  ) {
    Category created = categories.create(CategoryApiMapper.toCommand(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(created.id())
      .toUri();

    return ResponseEntity.created(location).body(CategoryApiMapper.toResponse(created));
  }

  @GetMapping("/roots")
  @RequireCategoriesRead
  public List<CategoryResponse> listRoots() {
    return categories.findRoots().stream().map(CategoryApiMapper::toResponse).toList();
  }

  @GetMapping
  @RequireCategoriesRead
  @Operation(
    summary = "List categories",
    description = "Returns categories with pagination. Ranked display order is exposed through tree sorting and scoped ascending sortRank sorting; descending or mixed-parent sortRank requests are rejected."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "Categories returned",
      content = @Content(schema = @Schema(implementation = PageResponse.class))
    ),
    @ApiResponse(
      responseCode = "400",
      description = "Invalid pagination or sorting parameters",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
    ),
    @ApiResponse(
      responseCode = "401",
      description = "Authentication is required",
      content = @Content
    ),
  })
  public PageResponse<CategoryListItemResponse> listCategories(
    @ParameterObject @Valid @ModelAttribute ListCategoriesRequest request
  ) {
    PageResult<CategoryNode> page = categories.findAll(CategoryApiMapper.toQuery(request));
    return CategoryApiMapper.toPageResponse(page, PageLinksAssembler.fromCurrentRequest(page));
  }

  @GetMapping("/tree")
  @RequireCategoriesRead
  public List<CategoryTreeResponse> listTree() {
    return categories.findTree().stream().map(CategoryApiMapper::toTreeResponse).toList();
  }

  @GetMapping("/{id}")
  @RequireCategoriesRead
  public CategoryListItemResponse getCategory(@PathVariable Long id) {
    return CategoryApiMapper.toListItemResponse(categories.findNodeById(id));
  }

  @GetMapping("/{id}/tree")
  @RequireCategoriesRead
  public CategoryTreeResponse getTree(@PathVariable Long id) {
    return CategoryApiMapper.toTreeResponse(categories.findTree(id));
  }

  @GetMapping("/{id}/children")
  @RequireCategoriesRead
  public List<CategoryResponse> listChildren(@PathVariable Long id) {
    return categories.findChildren(id).stream().map(CategoryApiMapper::toResponse).toList();
  }

  @GetMapping("/{id}/ancestors")
  @RequireCategoriesRead
  public List<CategoryResponse> listAncestors(@PathVariable Long id) {
    return categories.findAncestors(id).stream().map(CategoryApiMapper::toResponse).toList();
  }

  @GetMapping("/{id}/descendants")
  @RequireCategoriesRead
  public List<CategoryResponse> listDescendants(@PathVariable Long id) {
    return categories.findDescendants(id).stream().map(CategoryApiMapper::toResponse).toList();
  }

  @PutMapping("/{id}")
  @RequireCategoriesWrite
  public CategoryResponse updateCategory(
    @PathVariable Long id,
    @Valid @RequestBody UpdateCategoryRequest request
  ) {
    return CategoryApiMapper.toResponse(
      categories.update(id, CategoryApiMapper.toCommand(request))
    );
  }

  @PatchMapping("/{id}")
  @RequireCategoriesWrite
  public CategoryResponse patchCategory(
    @PathVariable Long id,
    @Valid @RequestBody PatchCategoryRequest request
  ) {
    return CategoryApiMapper.toResponse(categories.patch(id, CategoryApiMapper.toCommand(request)));
  }

  @PostMapping("/{id}/move")
  @RequireCategoriesWrite
  @Operation(
    summary = "Move category",
    description = "Moves a category to another parent. Ranked systems accept optional beforeId, afterId, or zero-based targetIndex placement fields for the destination sibling group."
  )
  public CategoryResponse moveCategory(
    @PathVariable Long id,
    @Valid @RequestBody MoveCategoryRequest request
  ) {
    return CategoryApiMapper.toResponse(categories.move(id, CategoryApiMapper.toCommand(request)));
  }

  @PostMapping("/{id}/reorder")
  @RequireCategoriesWrite
  @Operation(
    summary = "Reorder category",
    description = "Reorders a category within its current parent by append, beforeId, afterId, or zero-based targetIndex. Same-parent targetIndex calculations exclude the moved category."
  )
  public CategoryResponse reorderCategory(
    @PathVariable Long id,
    @Valid @RequestBody ReorderCategoryRequest request
  ) {
    return CategoryApiMapper.toResponse(
      categories.reorder(id, CategoryApiMapper.toCommand(request))
    );
  }

  @PostMapping("/{id}/move/estimate")
  @RequireCategoriesWrite
  public CategoryMoveEstimateResponse estimateMoveCategory(
    @PathVariable Long id,
    @Valid @RequestBody MoveCategoryRequest request
  ) {
    return CategoryApiMapper.toResponse(
      categories.estimateMove(id, CategoryApiMapper.toCommand(request))
    );
  }

  @DeleteMapping("/{id}")
  @RequireCategoriesWrite
  public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
    categories.delete(id);
    return ResponseEntity.noContent().build();
  }
}
