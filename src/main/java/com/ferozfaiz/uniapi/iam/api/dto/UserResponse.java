package com.ferozfaiz.uniapi.iam.api.dto;

import java.time.OffsetDateTime;

public record UserResponse(
  Long id,
  String username,
  String email,
  String firstName,
  String lastName,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
