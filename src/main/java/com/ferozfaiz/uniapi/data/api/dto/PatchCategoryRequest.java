package com.ferozfaiz.uniapi.data.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record PatchCategoryRequest(
  @Size(max = 150, message = "{data.api.category.slug.size}")
  @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "{data.api.category.slug.pattern}")
  @Nullable
  String slug,
  @Size(max = 255, message = "{data.api.category.name.size}") @Nullable String name,
  @Size(max = 1000, message = "{data.api.category.description.size}") @Nullable String description,
  @Nullable Boolean active
) {}
