package com.ferozfaiz.uniapi.category.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A bounded admin category parent candidate")
public record AdminCategoryParentOptionResponse(
  Long id,
  Long parentId,
  String name,
  String slug,
  boolean active,
  int depth,
  String label,
  List<AdminCategoryBreadcrumbItemResponse> path,
  boolean selectable,
  String disabledReason
) {}
