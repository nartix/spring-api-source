package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.api.validation.CatalogueSlug;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateProductRequest(
  @NotBlank(message = "{catalogue.api.product.title.required}")
  @Size(max = 255, message = "{catalogue.api.product.title.size}")
  String title,
  @CatalogueSlug String slug,
  @NotNull(message = "{catalogue.api.product.family.required}") ProductFamily family,
  @NotNull(message = "{catalogue.api.product.brand.required}") Long brandId,
  @NotNull(message = "{catalogue.api.product.manufacturer.required}") Long manufacturerId,
  @Size(max = 1000, message = "{catalogue.api.product.main-image-url.size}") String mainImageUrl,
  @Size(max = 1000, message = "{catalogue.api.product.short-description.size}")
  String shortDescription,
  @Size(max = 20, message = "{catalogue.api.product.identifiers.size}")
  List<
    @jakarta.validation.Valid @NotNull(
      message = "{catalogue.api.product.identifiers.invalid}"
    ) ProductIdentifierRequest
  > identifiers,
  Boolean active
) {}
