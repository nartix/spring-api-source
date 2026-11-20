package com.ferozfaiz.uniapi.catalogue.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueRead;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueWrite;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateProductRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchProductRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductDetailResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductListItemResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.ProductListingRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateProductRequest;
import com.ferozfaiz.uniapi.catalogue.api.mapper.CatalogueApiMapper;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.pagination.PageLinksAssembler;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
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
public class CatalogueController {

  private static final Set<String> ALLOWED_QUERY_PARAMETERS = Set.of(
    "page",
    "size",
    "sort",
    "search",
    "family",
    "category",
    "brand",
    "manufacturer",
    "merchantStore",
    "marketplace",
    "hasCurrentPrice",
    "storageType",
    "capacityGbMin",
    "capacityGbMax",
    "interfaceType",
    "formFactor",
    "readSpeedMbpsMin",
    "writeSpeedMbpsMin",
    "rpmMin",
    "cacheMbMin",
    "socket",
    "coreCountMin",
    "threadCountMin",
    "baseClockGhzMin",
    "boostClockGhzMin",
    "architecture",
    "integratedGraphics",
    "tdpWattsMax",
    "gpuChipset",
    "vramGbMin",
    "memoryType",
    "boostClockMhzMin",
    "lengthMmMax"
  );

  private final CatalogueQueryService queries;
  private final ProductApplicationService products;
  private final CatalogueListPolicy policy;

  public CatalogueController(
    CatalogueQueryService queries,
    ProductApplicationService products,
    CatalogueListPolicy policy
  ) {
    this.queries = queries;
    this.products = products;
    this.policy = policy;
  }

  @GetMapping("/products")
  @RequireCatalogueRead
  public PageResponse<ProductListItemResponse> listProducts(
    @ParameterObject @Valid @ModelAttribute ProductListingRequest request,
    HttpServletRequest servletRequest
  ) {
    rejectUnknownParameters(servletRequest);
    return list(request, null);
  }

  @GetMapping("/storage-products")
  @RequireCatalogueRead
  public PageResponse<ProductListItemResponse> listStorageProducts(
    @ParameterObject @Valid @ModelAttribute ProductListingRequest request,
    HttpServletRequest servletRequest
  ) {
    rejectUnknownParameters(servletRequest);
    return list(request, ProductFamily.STORAGE);
  }

  @GetMapping("/cpus")
  @RequireCatalogueRead
  public PageResponse<ProductListItemResponse> listCpus(
    @ParameterObject @Valid @ModelAttribute ProductListingRequest request,
    HttpServletRequest servletRequest
  ) {
    rejectUnknownParameters(servletRequest);
    return list(request, ProductFamily.CPU);
  }

  @GetMapping("/graphics-cards")
  @RequireCatalogueRead
  public PageResponse<ProductListItemResponse> listGraphicsCards(
    @ParameterObject @Valid @ModelAttribute ProductListingRequest request,
    HttpServletRequest servletRequest
  ) {
    rejectUnknownParameters(servletRequest);
    return list(request, ProductFamily.GPU);
  }

  @GetMapping("/products/{id}")
  @RequireCatalogueRead
  public ProductDetailResponse productDetail(
    @PathVariable Long id,
    @ParameterObject ProductListingRequest request
  ) {
    return CatalogueApiMapper.toDetailResponse(
      queries.productDetail(id, null, marketplace(request), request.merchantStore())
    );
  }

  @GetMapping("/products/{id}/{slug}")
  @RequireCatalogueRead
  public ProductDetailResponse productDetail(
    @PathVariable Long id,
    @PathVariable String slug,
    @ParameterObject ProductListingRequest request
  ) {
    return CatalogueApiMapper.toDetailResponse(
      queries.productDetail(id, slug, marketplace(request), request.merchantStore())
    );
  }

  @PostMapping("/products")
  @RequireCatalogueWrite
  public ResponseEntity<ProductDetailResponse> createProduct(
    @Valid @RequestBody CreateProductRequest request
  ) {
    var product = products.create(CatalogueApiMapper.toCommand(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(product.id())
      .toUri();
    return ResponseEntity.created(location).body(CatalogueApiMapper.toDetailResponse(product));
  }

  @PutMapping("/products/{id}")
  @RequireCatalogueWrite
  public ProductDetailResponse updateProduct(
    @PathVariable Long id,
    @Valid @RequestBody UpdateProductRequest request
  ) {
    return CatalogueApiMapper.toDetailResponse(
      products.update(CatalogueApiMapper.toCommand(id, request))
    );
  }

  @PatchMapping(
    value = "/products/{id}",
    consumes = { MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json" }
  )
  @RequireCatalogueWrite
  public ProductDetailResponse patchProduct(@PathVariable Long id, @RequestBody ObjectNode patch) {
    var existing = products.get(id);
    var command = CatalogueApiMapper.toCommand(existing, PatchProductRequest.from(patch));
    return CatalogueApiMapper.toDetailResponse(products.update(command));
  }

  @DeleteMapping("/products/{id}")
  @RequireCatalogueWrite
  public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    products.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/products/{id}/activate")
  @RequireCatalogueWrite
  public ResponseEntity<Void> activateProduct(@PathVariable Long id) {
    products.activate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/products/{id}/deactivate")
  @RequireCatalogueWrite
  public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
    products.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/products/{id}/categories/{categoryId}")
  @RequireCatalogueWrite
  public ResponseEntity<Void> assignCategory(@PathVariable Long id, @PathVariable Long categoryId) {
    products.assignCategory(id, categoryId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/products/{id}/categories/{categoryId}")
  @RequireCatalogueWrite
  public ResponseEntity<Void> removeCategory(@PathVariable Long id, @PathVariable Long categoryId) {
    products.removeCategory(id, categoryId);
    return ResponseEntity.noContent().build();
  }

  private PageResponse<ProductListItemResponse> list(
    ProductListingRequest request,
    ProductFamily fixedFamily
  ) {
    ListProductsQuery query = request.toQuery(
      policy.defaultPageSize(),
      policy.defaultMarketplace(),
      fixedFamily
    );
    var page = queries.listProducts(query);
    return CatalogueApiMapper.toPageResponse(page, PageLinksAssembler.fromCurrentRequest(page));
  }

  private String marketplace(ProductListingRequest request) {
    return request.marketplace() == null || request.marketplace().isBlank()
      ? policy.defaultMarketplace()
      : request.marketplace().trim().toUpperCase(java.util.Locale.ROOT);
  }

  static void rejectUnknownParameters(HttpServletRequest request) {
    for (String parameter : request.getParameterMap().keySet()) {
      if (!ALLOWED_QUERY_PARAMETERS.contains(parameter)) {
        throw new com.ferozfaiz.uniapi.shared.error.InvalidRequestException(
          "catalogue.api.list.filter.unknown",
          "filter is not supported: " + parameter,
          parameter
        );
      }
    }
  }
}
