package com.ferozfaiz.uniapi.shared.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pagination navigation links")
public record PageLinks(
  @Schema(
    description = "Current page link",
    example = "/api/v1/resources?page=0&size=20&sort=createdAt,desc"
  )
  String self,
  @Schema(
    description = "Next page link when available",
    nullable = true,
    example = "/api/v1/resources?page=1&size=20&sort=createdAt,desc"
  )
  String next,
  @Schema(
    description = "Previous page link when available",
    nullable = true,
    example = "/api/v1/resources?page=0&size=20&sort=createdAt,desc"
  )
  String previous
) {}
