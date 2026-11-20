package com.ferozfaiz.uniapi.category.api.dto;

import java.time.OffsetDateTime;

public record AdminCategoryDetailResponse(
  Long id,
  Long parentId,
  Long sortRank,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
