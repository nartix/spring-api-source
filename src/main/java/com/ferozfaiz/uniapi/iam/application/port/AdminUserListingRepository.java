package com.ferozfaiz.uniapi.iam.application.port;

import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;

public interface AdminUserListingRepository {
  PageResult<AdminUserListItem> findAll(AdminListUsersQuery query);
}
