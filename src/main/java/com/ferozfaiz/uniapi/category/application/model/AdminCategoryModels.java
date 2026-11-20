package com.ferozfaiz.uniapi.category.application.model;

import java.time.OffsetDateTime;
import java.util.List;

public final class AdminCategoryModels {

  private AdminCategoryModels() {}

  public record Category(
    Long id,
    Long parentId,
    Long sortRank,
    String name,
    String slug,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
  ) {}

  public record CategoryNode(Category category, int depth, boolean hasChildren) {}

  public record BreadcrumbItem(Category category, int depth) {}

  public record ParentOption(
    Category category,
    int depth,
    String label,
    List<BreadcrumbItem> path,
    boolean selectable,
    String disabledReason
  ) {
    public ParentOption {
      path = List.copyOf(path);
    }
  }

  public record ParentOptionSlice(List<ParentOption> data, boolean hasMore, String nextCursor) {
    public ParentOptionSlice {
      data = List.copyOf(data);
    }
  }
}
