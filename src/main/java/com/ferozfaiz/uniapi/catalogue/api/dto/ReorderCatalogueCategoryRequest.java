package com.ferozfaiz.uniapi.catalogue.api.dto;

import jakarta.validation.constraints.Min;
import org.springframework.lang.Nullable;

public record ReorderCatalogueCategoryRequest(
  @Nullable Long parentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Min(value = 0, message = "{catalogue.api.category.placement.target-index.bounds}")
  @Nullable
  Integer targetIndex
) {}
