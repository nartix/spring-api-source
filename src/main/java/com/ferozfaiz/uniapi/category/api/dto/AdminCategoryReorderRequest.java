package com.ferozfaiz.uniapi.category.api.dto;

import org.springframework.lang.Nullable;

public record AdminCategoryReorderRequest(
  @Nullable Long parentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Nullable Integer targetIndex
) {}
