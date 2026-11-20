package com.ferozfaiz.uniapi.iam.application.query;

import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record AdminListUsersQuery(
  PageCriteria page,
  SortCriteria sort,
  String search,
  Long id,
  String username,
  String email,
  String firstName,
  String lastName,
  Boolean active,
  Boolean accountNonExpired,
  Boolean accountNonLocked,
  Boolean credentialsNonExpired,
  OffsetDateTime lastLoginFrom,
  OffsetDateTime lastLoginTo,
  OffsetDateTime lastActivityFrom,
  OffsetDateTime lastActivityTo,
  OffsetDateTime createdFrom,
  OffsetDateTime createdTo,
  OffsetDateTime updatedFrom,
  OffsetDateTime updatedTo,
  String createdBy,
  String updatedBy,
  List<String> roles,
  List<String> permissions
) {
  public AdminListUsersQuery {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(sort, "sort must not be null");
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }
}
