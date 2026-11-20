package com.ferozfaiz.uniapi.catalogue.api.dto;

import java.time.OffsetDateTime;

public record CatalogueCategoryResponse(
  Long id,
  Long parentId,
  Long sortRank,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
