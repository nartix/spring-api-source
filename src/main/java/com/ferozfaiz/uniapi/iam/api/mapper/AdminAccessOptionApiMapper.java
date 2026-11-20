package com.ferozfaiz.uniapi.iam.api.mapper;

import com.ferozfaiz.uniapi.iam.api.dto.AdminListPermissionsRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminListRolesRequest;
import com.ferozfaiz.uniapi.iam.api.dto.AdminPermissionOptionResponse;
import com.ferozfaiz.uniapi.iam.api.dto.AdminRoleOptionResponse;
import com.ferozfaiz.uniapi.iam.application.query.AdminListPermissionsQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminListRolesQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.shared.pagination.PageLinks;
import com.ferozfaiz.uniapi.shared.pagination.PageResponse;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;

public final class AdminAccessOptionApiMapper {

  private AdminAccessOptionApiMapper() {}

  public static AdminListRolesQuery toQuery(AdminListRolesRequest request) {
    return request.toQuery();
  }

  public static AdminListPermissionsQuery toQuery(AdminListPermissionsRequest request) {
    return request.toQuery();
  }

  public static AdminRoleOptionResponse toResponse(Role role) {
    return new AdminRoleOptionResponse(
      String.valueOf(role.id()),
      role.code(),
      role.name(),
      role.description(),
      role.active()
    );
  }

  public static AdminPermissionOptionResponse toResponse(Permission permission) {
    return new AdminPermissionOptionResponse(
      String.valueOf(permission.id()),
      permission.code(),
      permission.name(),
      permission.description(),
      permission.resource(),
      permission.action(),
      permission.active()
    );
  }

  public static PageResponse<AdminRoleOptionResponse> toRolePageResponse(
    PageResult<Role> page,
    PageLinks links
  ) {
    return PageResponse.from(page, AdminAccessOptionApiMapper::toResponse, links);
  }

  public static PageResponse<AdminPermissionOptionResponse> toPermissionPageResponse(
    PageResult<Permission> page,
    PageLinks links
  ) {
    return PageResponse.from(page, AdminAccessOptionApiMapper::toResponse, links);
  }
}
