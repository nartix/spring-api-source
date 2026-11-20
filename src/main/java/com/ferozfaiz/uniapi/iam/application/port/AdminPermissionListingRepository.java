package com.ferozfaiz.uniapi.iam.application.port;

import com.ferozfaiz.uniapi.iam.application.query.AdminListPermissionsQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;

public interface AdminPermissionListingRepository {
  PageResult<Permission> findAll(AdminListPermissionsQuery query);
}
