package com.ferozfaiz.uniapi.data.api.dto;

import jakarta.validation.constraints.Min;
import org.springframework.lang.Nullable;

public record ReorderCategoryRequest(
  @Nullable Long parentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Min(value = 0, message = "{data.api.category.placement.target-index.bounds}")
  @Nullable
  Integer targetIndex
) {}
