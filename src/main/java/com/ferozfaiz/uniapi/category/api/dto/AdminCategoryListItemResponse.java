package com.ferozfaiz.uniapi.category.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Generic admin category list item")
public record AdminCategoryListItemResponse(
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
