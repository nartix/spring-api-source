package com.ferozfaiz.uniapi.catalogue.api.dto;

import java.time.OffsetDateTime;

public record CatalogueCategoryTreeItemResponse(
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
