package com.ferozfaiz.uniapi.iam.application.query;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminUserListItem(
  Long id,
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
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt,
  String createdBy,
  String updatedBy,
  List<String> roles,
  List<String> permissions
) {
  public AdminUserListItem {
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }
}
