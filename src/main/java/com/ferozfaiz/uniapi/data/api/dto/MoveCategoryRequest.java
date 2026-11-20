package com.ferozfaiz.uniapi.data.api.dto;

import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import jakarta.validation.constraints.Min;
import org.springframework.lang.Nullable;

public record MoveCategoryRequest(
  @Nullable Long parentId,
  @Nullable CategoryMoveMode mode,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Min(value = 0, message = "{data.api.category.placement.target-index.bounds}")
  @Nullable
  Integer targetIndex
) {
  public CategoryMoveMode effectiveMode() {
    return mode == null ? CategoryMoveMode.LEAF_ONLY : mode;
  }
}
