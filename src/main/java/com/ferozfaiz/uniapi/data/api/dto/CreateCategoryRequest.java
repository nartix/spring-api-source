package com.ferozfaiz.uniapi.data.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateCategoryRequest(
  @Nullable Long parentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Min(value = 0, message = "{data.api.category.placement.target-index.bounds}")
  @Nullable
  Integer targetIndex,
  @NotBlank(message = "{data.api.category.slug.required}")
  @Size(max = 150, message = "{data.api.category.slug.size}")
  @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "{data.api.category.slug.pattern}")
  String slug,
  @NotBlank(message = "{data.api.category.name.required}")
  @Size(max = 255, message = "{data.api.category.name.size}")
  String name,
  @Size(max = 1000, message = "{data.api.category.description.size}") @Nullable String description,
  @Nullable Boolean active
) {}
