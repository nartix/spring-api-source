package com.ferozfaiz.uniapi.iam.application.port;

import com.ferozfaiz.uniapi.iam.application.query.AdminListRolesQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;

public interface AdminRoleListingRepository {
  PageResult<Role> findAll(AdminListRolesQuery query);
}
