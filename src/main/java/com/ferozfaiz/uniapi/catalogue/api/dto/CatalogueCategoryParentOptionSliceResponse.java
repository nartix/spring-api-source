package com.ferozfaiz.uniapi.catalogue.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "No-total-count admin category parent option slice")
public record CatalogueCategoryParentOptionSliceResponse(
  List<CatalogueCategoryParentOptionResponse> data,
  boolean hasMore,
  String nextCursor
) {}
