package com.ferozfaiz.uniapi.catalogue.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueRead;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueWrite;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryCapabilityResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CatalogueCategoryResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.MoveCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductListItemResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductListingRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ReorderCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateCatalogueCategoryRequest;
import com.ferozfaiz.uniapi.catalogue.api.mapper.CatalogueApiMapper;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategoryCapabilityMetadata;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.shared.pagination.PageLinksAssembler;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("${spring.data.rest.base-path}")
public class CatalogueCategoryController {

  private static final String CATEGORY_SYSTEM = "catalogue";

  private final CatalogueQueryService queries;
  private final CatalogueCategoryApplicationService categories;
  private final CatalogueListPolicy policy;
  private final CategorySystemRegistry categorySystems;

  public CatalogueCategoryController(
    CatalogueQueryService queries,
    CatalogueCategoryApplicationService categories,
    CatalogueListPolicy policy,
    CategorySystemRegistry categorySystems
  ) {
    this.queries = queries;
    this.categories = categories;
    this.policy = policy;
    this.categorySystems = categorySystems;
  }

  @GetMapping("/catalogue/categories/capabilities")
  @RequireCatalogueRead
  @Operation(
    summary = "Get public catalogue category ranking capabilities",
    description = "Describes whether catalogue category writes support sparse ranking, which placement modes clients may send, and whether category responses expose sortRank metadata."
  )
  public CatalogueCategoryCapabilityResponse capabilities() {
    return CatalogueApiMapper.toCapabilityResponse(
      CategoryCapabilityMetadata.from(categorySystems.requireDescriptor(CATEGORY_SYSTEM))
    );
  }

  @GetMapping("/categories/{slug}/products")
  @RequireCatalogueRead
  public PageResponse<ProductListItemResponse> listCategoryProducts(
    @PathVariable String slug,
    @ParameterObject @Valid @ModelAttribute ProductListingRequest request,
    HttpServletRequest servletRequest
  ) {
    CatalogueController.rejectUnknownParameters(servletRequest);
    var query = request.toQuery(policy.defaultPageSize(), policy.defaultMarketplace(), null);
    var page = queries.listProductsInCategory(slug, query);
    return CatalogueApiMapper.toPageResponse(page, PageLinksAssembler.fromCurrentRequest(page));
  }

  @PostMapping("/catalogue/categories")
  @RequireCatalogueWrite
  @Operation(
    summary = "Create catalogue category",
    description = "Creates a catalogue category. Ranked systems accept optional beforeId, afterId, or zero-based targetIndex placement fields and return sortRank metadata."
  )
  public ResponseEntity<CatalogueCategoryResponse> createCategory(
    @Valid @RequestBody CreateCatalogueCategoryRequest request
  ) {
    var category = categories.create(CatalogueApiMapper.toCommand(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(category.id())
      .toUri();
    return ResponseEntity.created(location).body(CatalogueApiMapper.toCategoryResponse(category));
  }

  @PutMapping("/catalogue/categories/{id}")
  @RequireCatalogueWrite
  public CatalogueCategoryResponse updateCategory(
    @PathVariable Long id,
    @Valid @RequestBody UpdateCatalogueCategoryRequest request
  ) {
    return CatalogueApiMapper.toCategoryResponse(
      categories.update(CatalogueApiMapper.toCommand(id, request))
    );
  }

  @PatchMapping(
    value = "/catalogue/categories/{id}",
    consumes = { MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json" }
  )
  @RequireCatalogueWrite
  public CatalogueCategoryResponse patchCategory(
    @PathVariable Long id,
    @RequestBody ObjectNode patch
  ) {
    var existing = categories.get(id);
    var command = CatalogueApiMapper.toCommand(existing, PatchCatalogueCategoryRequest.from(patch));
    return CatalogueApiMapper.toCategoryResponse(categories.update(command));
  }

  @DeleteMapping("/catalogue/categories/{id}")
  @RequireCatalogueWrite
  public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
    categories.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/catalogue/categories/{id}/move")
  @RequireCatalogueWrite
  @Operation(
    summary = "Move catalogue category",
    description = "Moves a catalogue category to a destination parent. Ranked systems accept optional beforeId, afterId, or zero-based targetIndex placement fields."
  )
  public ResponseEntity<Void> moveCategory(
    @PathVariable Long id,
    @Valid @RequestBody MoveCatalogueCategoryRequest request
  ) {
    categories.move(id, request.newParentId(), CatalogueApiMapper.toOrderTarget(request));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/catalogue/categories/{id}/reorder")
  @RequireCatalogueWrite
  @Operation(
    summary = "Reorder catalogue category",
    description = "Reorders a catalogue category within its current parent by append, beforeId, afterId, or zero-based targetIndex."
  )
  public CatalogueCategoryResponse reorderCategory(
    @PathVariable Long id,
    @Valid @RequestBody ReorderCatalogueCategoryRequest request
  ) {
    return CatalogueApiMapper.toCategoryResponse(
      categories.reorder(id, CatalogueApiMapper.toCommand(request))
    );
  }

  @PostMapping("/catalogue/categories/{id}/activate")
  @RequireCatalogueWrite
  public ResponseEntity<Void> activateCategory(@PathVariable Long id) {
    categories.activate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/catalogue/categories/{id}/deactivate")
  @RequireCatalogueWrite
  public ResponseEntity<Void> deactivateCategory(@PathVariable Long id) {
    categories.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
