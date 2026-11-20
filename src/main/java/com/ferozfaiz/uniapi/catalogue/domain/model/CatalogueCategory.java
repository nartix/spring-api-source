package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

public record CatalogueCategory(
  Long id,
  @Nullable Long parentId,
  @Nullable Long sortRank,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {
  public CatalogueCategory(
    Long id,
    @Nullable Long parentId,
    String name,
    String slug,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
  ) {
    this(id, parentId, null, name, slug, active, createdAt, updatedAt);
  }

  public record Hierarchy(Long categoryId, List<Long> ancestorIds, List<Long> descendantIds) {
    public Hierarchy {
      ancestorIds = List.copyOf(ancestorIds);
      descendantIds = List.copyOf(descendantIds);
    }
  }
}
