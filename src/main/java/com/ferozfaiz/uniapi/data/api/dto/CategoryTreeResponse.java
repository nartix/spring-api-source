package com.ferozfaiz.uniapi.data.api.dto;

import java.util.List;
import org.springframework.lang.Nullable;

public record CategoryTreeResponse(
  Long id,
  @Nullable Long parentId,
  @Nullable Long sortRank,
  String slug,
  String name,
  @Nullable String description,
  boolean active,
  List<CategoryTreeResponse> children
) {
  public CategoryTreeResponse {
    children = List.copyOf(children);
  }
}
