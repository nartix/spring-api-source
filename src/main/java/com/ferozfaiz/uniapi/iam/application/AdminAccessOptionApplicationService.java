package com.ferozfaiz.uniapi.iam.application;

import com.ferozfaiz.uniapi.iam.application.port.AdminPermissionListingRepository;
import com.ferozfaiz.uniapi.iam.application.port.AdminRoleListingRepository;
import com.ferozfaiz.uniapi.iam.application.query.AdminListPermissionsQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminListRolesQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAccessOptionApplicationService {

  private final AdminRoleListingRepository roles;
  private final AdminPermissionListingRepository permissions;

  public AdminAccessOptionApplicationService(
    AdminRoleListingRepository roles,
    AdminPermissionListingRepository permissions
  ) {
    this.roles = roles;
    this.permissions = permissions;
  }

  public PageResult<Role> findRoles(AdminListRolesQuery query) {
    return roles.findAll(query);
  }

  public PageResult<Permission> findPermissions(AdminListPermissionsQuery query) {
    return permissions.findAll(query);
  }
}
