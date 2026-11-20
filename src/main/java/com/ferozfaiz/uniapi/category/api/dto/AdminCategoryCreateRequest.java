package com.ferozfaiz.uniapi.category.api.dto;

import org.springframework.lang.Nullable;

public record AdminCategoryCreateRequest(
  @Nullable Long parentId,
  @Nullable Long beforeId,
  @Nullable Long afterId,
  @Nullable Integer targetIndex,
  String name,
  String slug,
  Boolean active
) {}
