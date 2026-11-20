package com.ferozfaiz.uniapi.iam.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record UserPermission(
  @Nullable Long id,
  Long userId,
  Long permissionId,
  OffsetDateTime grantedAt,
  @Nullable OffsetDateTime expiresAt,
  @Nullable String createdBy
) {}
