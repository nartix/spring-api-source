package com.ferozfaiz.uniapi.iam.domain.model;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record User(
  @Nullable Long id,
  String username,
  String email,
  String passwordHash,
  @Nullable String firstName,
  @Nullable String lastName,
  boolean active,
  boolean accountNonExpired,
  boolean accountNonLocked,
  boolean credentialsNonExpired,
  @Nullable OffsetDateTime lastLoginAt,
  @Nullable OffsetDateTime lastActivityAt,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt,
  @Nullable String createdBy,
  @Nullable String updatedBy
) {}
