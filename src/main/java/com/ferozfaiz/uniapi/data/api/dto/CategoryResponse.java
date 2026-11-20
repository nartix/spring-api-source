package com.ferozfaiz.uniapi.data.api.dto;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record CategoryResponse(
  Long id,
  @Nullable Long parentId,
  @Nullable Long sortRank,
  String slug,
  String name,
  @Nullable String description,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
