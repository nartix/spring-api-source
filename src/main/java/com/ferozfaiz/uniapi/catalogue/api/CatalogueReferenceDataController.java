package com.ferozfaiz.uniapi.catalogue.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueRead;
import com.ferozfaiz.uniapi.catalogue.api.authorization.RequireCatalogueWrite;
import com.ferozfaiz.uniapi.catalogue.api.dto.BrandResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateBrandRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.CreateManufacturerRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ManufacturerResponse;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchBrandRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.PatchManufacturerRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.ReferenceDataListingRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateBrandRequest;
import com.ferozfaiz.uniapi.catalogue.api.dto.UpdateManufacturerRequest;
import com.ferozfaiz.uniapi.catalogue.api.mapper.CatalogueApiMapper;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
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
public class CatalogueReferenceDataController {

  private static final Set<String> ALLOWED_QUERY_PARAMETERS = Set.of(
    "page",
    "size",
    "sort",
    "search",
    "active"
  );

  private final CatalogueReferenceDataApplicationService referenceData;

  public CatalogueReferenceDataController(CatalogueReferenceDataApplicationService referenceData) {
    this.referenceData = referenceData;
  }

  @GetMapping("/catalogue/brands")
  @RequireCatalogueRead
  public PageResponse<BrandResponse> listBrands(
    @ParameterObject @Valid @ModelAttribute ReferenceDataListingRequest request,
    HttpServletRequest servletRequest
  ) {
    rejectUnknownParameters(servletRequest);
    var page = referenceData.listBrands(request.toQuery());
    return PageResponse.from(
      page,
      CatalogueApiMapper::toBrandResponse,
      PageLinksAssembler.fromCurrentRequest(page)
    );
  }

  @GetMapping("/catalogue/brands/{id}")
  @RequireCatalogueRead
  public BrandResponse getBrand(@PathVariable Long id) {
    return CatalogueApiMapper.toBrandResponse(referenceData.getBrand(id));
  }

  @PostMapping("/catalogue/brands")
  @RequireCatalogueWrite
  public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
    var brand = referenceData.createOrUpdateBrand(CatalogueApiMapper.toCommand(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(brand.id())
      .toUri();
    return ResponseEntity.created(location).body(CatalogueApiMapper.toBrandResponse(brand));
  }

  @PutMapping("/catalogue/brands/{id}")
  @RequireCatalogueWrite
  public BrandResponse updateBrand(
    @PathVariable Long id,
    @Valid @RequestBody UpdateBrandRequest request
  ) {
    return CatalogueApiMapper.toBrandResponse(
      referenceData.createOrUpdateBrand(CatalogueApiMapper.toCommand(id, request))
    );
  }

  @PatchMapping(
    value = "/catalogue/brands/{id}",
    consumes = { MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json" }
  )
  @RequireCatalogueWrite
  public BrandResponse patchBrand(@PathVariable Long id, @RequestBody ObjectNode patch) {
    var existing = referenceData.getBrand(id);
    var command = CatalogueApiMapper.toCommand(existing, PatchBrandRequest.from(patch));
    return CatalogueApiMapper.toBrandResponse(referenceData.createOrUpdateBrand(command));
  }

  @DeleteMapping("/catalogue/brands/{id}")
  @RequireCatalogueWrite
  public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
    referenceData.deleteBrand(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/catalogue/manufacturers")
  @RequireCatalogueRead
  public PageResponse<ManufacturerResponse> listManufacturers(
    @ParameterObject @Valid @ModelAttribute ReferenceDataListingRequest request,
    HttpServletRequest servletRequest
  ) {
    rejectUnknownParameters(servletRequest);
    var page = referenceData.listManufacturers(request.toQuery());
    return PageResponse.from(
      page,
      CatalogueApiMapper::toManufacturerResponse,
      PageLinksAssembler.fromCurrentRequest(page)
    );
  }

  @GetMapping("/catalogue/manufacturers/{id}")
  @RequireCatalogueRead
  public ManufacturerResponse getManufacturer(@PathVariable Long id) {
    return CatalogueApiMapper.toManufacturerResponse(referenceData.getManufacturer(id));
  }

  @PostMapping("/catalogue/manufacturers")
  @RequireCatalogueWrite
  public ResponseEntity<ManufacturerResponse> createManufacturer(
    @Valid @RequestBody CreateManufacturerRequest request
  ) {
    var manufacturer = referenceData.createOrUpdateManufacturer(
      CatalogueApiMapper.toCommand(request)
    );
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(manufacturer.id())
      .toUri();
    return ResponseEntity.created(location).body(
      CatalogueApiMapper.toManufacturerResponse(manufacturer)
    );
  }

  @PutMapping("/catalogue/manufacturers/{id}")
  @RequireCatalogueWrite
  public ManufacturerResponse updateManufacturer(
    @PathVariable Long id,
    @Valid @RequestBody UpdateManufacturerRequest request
  ) {
    return CatalogueApiMapper.toManufacturerResponse(
      referenceData.createOrUpdateManufacturer(CatalogueApiMapper.toCommand(id, request))
    );
  }

  @PatchMapping(
    value = "/catalogue/manufacturers/{id}",
    consumes = { MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json" }
  )
  @RequireCatalogueWrite
  public ManufacturerResponse patchManufacturer(
    @PathVariable Long id,
    @RequestBody ObjectNode patch
  ) {
    var existing = referenceData.getManufacturer(id);
    var command = CatalogueApiMapper.toCommand(existing, PatchManufacturerRequest.from(patch));
    return CatalogueApiMapper.toManufacturerResponse(
      referenceData.createOrUpdateManufacturer(command)
    );
  }

  @DeleteMapping("/catalogue/manufacturers/{id}")
  @RequireCatalogueWrite
  public ResponseEntity<Void> deleteManufacturer(@PathVariable Long id) {
    referenceData.deleteManufacturer(id);
    return ResponseEntity.noContent().build();
  }

  private static void rejectUnknownParameters(HttpServletRequest request) {
    for (String parameter : request.getParameterMap().keySet()) {
      if (!ALLOWED_QUERY_PARAMETERS.contains(parameter)) {
        throw new InvalidRequestException(
          "catalogue.api.list.filter.unknown",
          "filter is not supported: " + parameter,
          parameter
        );
      }
    }
  }
}
