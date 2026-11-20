package com.ferozfaiz.uniapi.iam.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record RolePermission(
  @Nullable Long id,
  Long roleId,
  Long permissionId,
  OffsetDateTime grantedAt,
  @Nullable String createdBy
) {}
