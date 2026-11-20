package com.ferozfaiz.uniapi.catalogue.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductIdentifierRequest(
  @NotBlank(message = "{catalogue.api.product.identifier.family.required}")
  @Size(max = 20, message = "{catalogue.api.product.identifier.family.size}")
  String family,
  @NotBlank(message = "{catalogue.api.product.identifier.value.required}")
  @Size(max = 32, message = "{catalogue.api.product.identifier.value.size}")
  String value,
  Boolean primary
) {}
