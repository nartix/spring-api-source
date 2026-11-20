package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.api.validation.CatalogueSlug;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateCatalogueCategoryRequest(
  @Nullable Long parentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Min(value = 0, message = "{catalogue.api.category.placement.target-index.bounds}")
  @Nullable
  Integer targetIndex,
  @NotBlank(message = "{catalogue.api.category.name.required}")
  @Size(max = 255, message = "{catalogue.api.category.name.size}")
  String name,
  @CatalogueSlug String slug,
  Boolean active
) {}
