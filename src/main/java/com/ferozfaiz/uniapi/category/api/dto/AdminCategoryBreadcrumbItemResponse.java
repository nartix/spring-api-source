package com.ferozfaiz.uniapi.category.api.dto;

import java.time.OffsetDateTime;

public record AdminCategoryBreadcrumbItemResponse(
  Long id,
  Long parentId,
  Long sortRank,
  int depth,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
