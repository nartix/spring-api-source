package com.ferozfaiz.uniapi.category.api.dto;

import java.time.OffsetDateTime;

public record AdminCategoryTreeItemResponse(
  Long id,
  Long parentId,
  Long sortRank,
  int depth,
  boolean hasChildren,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
