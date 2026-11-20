package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.api.validation.CatalogueSlug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateManufacturerRequest(
  @NotBlank(message = "{catalogue.api.manufacturer.name.required}")
  @Size(max = 255, message = "{catalogue.api.manufacturer.name.size}")
  String name,
  @CatalogueSlug String slug,
  Boolean active
) {}
