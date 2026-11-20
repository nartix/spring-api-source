package com.ferozfaiz.uniapi.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminUserResponse(
  String id,
  String username,
  String email,
  String firstName,
  String lastName,
  boolean active,
  boolean accountNonExpired,
  boolean accountNonLocked,
  boolean credentialsNonExpired,
  OffsetDateTime lastLoginAt,
  OffsetDateTime lastActivityAt,
  List<String> roles,
  List<String> permissions,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt,
  String createdBy,
  String updatedBy
) {}
