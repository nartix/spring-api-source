package com.ferozfaiz.uniapi.iam.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record Role(
  @Nullable Long id,
  String code,
  String name,
  @Nullable String description,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt,
  @Nullable String createdBy,
  @Nullable String updatedBy
) {}
