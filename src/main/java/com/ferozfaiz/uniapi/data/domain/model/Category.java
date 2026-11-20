package com.ferozfaiz.uniapi.data.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record Category(
  @Nullable Long id,
  @Nullable Long parentId,
  @Nullable Long sortRank,
  String slug,
  String name,
  @Nullable String description,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt,
  @Nullable String createdBy,
  @Nullable String updatedBy
) {
  public Category(
    @Nullable Long id,
    @Nullable Long parentId,
    String slug,
    String name,
    @Nullable String description,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @Nullable String createdBy,
    @Nullable String updatedBy
  ) {
    this(
      id,
      parentId,
      null,
      slug,
      name,
      description,
      active,
      createdAt,
      updatedAt,
      createdBy,
      updatedBy
    );
  }
}
