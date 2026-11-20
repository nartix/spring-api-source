package com.ferozfaiz.uniapi.catalogue.api.dto;

import java.time.OffsetDateTime;

public record CatalogueCategoryBreadcrumbItemResponse(
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
