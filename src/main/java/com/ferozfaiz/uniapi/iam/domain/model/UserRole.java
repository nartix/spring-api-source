package com.ferozfaiz.uniapi.iam.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record UserRole(
  @Nullable Long id,
  Long userId,
  Long roleId,
  OffsetDateTime assignedAt,
  @Nullable OffsetDateTime expiresAt,
  @Nullable String createdBy
) {}
