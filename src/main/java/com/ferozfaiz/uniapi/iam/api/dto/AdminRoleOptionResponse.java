package com.ferozfaiz.uniapi.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Assignable role option for admin user management")
public record AdminRoleOptionResponse(
  @Schema(description = "Role id", example = "1") String id,
  @Schema(description = "Stable role code submitted by admin user forms", example = "ADMIN")
  String code,
  @Schema(description = "Display role name", example = "Administrator") String name,
  @Schema(description = "Role description") String description,
  @Schema(description = "Whether the role can be assigned", example = "true") boolean active
) {}
