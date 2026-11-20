package com.ferozfaiz.uniapi.catalogue.api.dto;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionDisabledReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A bounded admin category parent candidate")
public record CatalogueCategoryParentOptionResponse(
  Long id,
  Long parentId,
  String name,
  String slug,
  boolean active,
  int depth,
  String label,
  List<CatalogueCategoryBreadcrumbItemResponse> path,
  boolean selectable,
  CatalogueCategoryParentOptionDisabledReason disabledReason
) {}
