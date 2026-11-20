package com.ferozfaiz.uniapi.iam.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record Permission(
  @Nullable Long id,
  String code,
  String name,
  @Nullable String description,
  String resource,
  String action,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt,
  @Nullable String createdBy,
  @Nullable String updatedBy
) {}
