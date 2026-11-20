package com.ferozfaiz.uniapi.category.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "No-total-count admin category parent option slice")
public record AdminCategoryParentOptionSliceResponse(
  List<AdminCategoryParentOptionResponse> data,
  boolean hasMore,
  String nextCursor
) {}
