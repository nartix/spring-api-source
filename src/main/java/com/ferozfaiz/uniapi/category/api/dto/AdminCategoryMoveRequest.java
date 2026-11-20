package com.ferozfaiz.uniapi.category.api.dto;

import org.springframework.lang.Nullable;

public record AdminCategoryMoveRequest(
  @Nullable Long newParentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Nullable Integer targetIndex
) {}
