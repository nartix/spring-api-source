package com.ferozfaiz.uniapi.iam.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Assignable permission option for admin user management")
public record AdminPermissionOptionResponse(
  @Schema(description = "Permission id", example = "10") String id,
  @Schema(
    description = "Stable permission code submitted by admin user forms",
    example = "admin:users:read"
  )
  String code,
  @Schema(description = "Display permission name", example = "Read admin users") String name,
  @Schema(description = "Permission description") String description,
  @Schema(description = "Permission resource", example = "admin-users") String resource,
  @Schema(description = "Permission action", example = "read") String action,
  @Schema(description = "Whether the permission can be assigned", example = "true") boolean active
) {}
