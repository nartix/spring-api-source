package com.ferozfaiz.uniapi.iam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.iam.application.port.AdminPermissionListingRepository;
import com.ferozfaiz.uniapi.iam.application.port.AdminRoleListingRepository;
import com.ferozfaiz.uniapi.iam.application.query.AdminListPermissionsQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminListRolesQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminAccessOptionApplicationServiceTest {

  private final FakeAdminRoleListingRepository roles = new FakeAdminRoleListingRepository();
  private final FakeAdminPermissionListingRepository permissions =
    new FakeAdminPermissionListingRepository();
  private final AdminAccessOptionApplicationService service =
    new AdminAccessOptionApplicationService(roles, permissions);

  @Test
  void findRolesDelegatesToRoleListingRepository() {
    AdminListRolesQuery query = new AdminListRolesQuery(
      new PageCriteria(0, 100),
      SortCriteria.of("code", SortDirection.ASC),
      "admin",
      true
    );

    PageResult<Role> result = service.findRoles(query);

    assertThat(roles.lastQuery).isSameAs(query);
    assertThat(result.content()).extracting(Role::code).containsExactly("ADMIN");
  }

  @Test
  void findPermissionsDelegatesToPermissionListingRepository() {
    AdminListPermissionsQuery query = new AdminListPermissionsQuery(
      new PageCriteria(0, 20),
      SortCriteria.of("code", SortDirection.ASC),
      "users",
      "admin-users",
      "read",
      true
    );

    PageResult<Permission> result = service.findPermissions(query);

    assertThat(permissions.lastQuery).isSameAs(query);
    assertThat(result.content()).extracting(Permission::code).containsExactly("admin:users:read");
  }

  private static final class FakeAdminRoleListingRepository implements AdminRoleListingRepository {

    private AdminListRolesQuery lastQuery;

    @Override
    public PageResult<Role> findAll(AdminListRolesQuery query) {
      lastQuery = query;
      return new PageResult<>(
        List.of(role()),
        query.page().page(),
        query.page().size(),
        1,
        1,
        true,
        true,
        query.sort()
      );
    }
  }

  private static final class FakeAdminPermissionListingRepository
    implements AdminPermissionListingRepository
  {

    private AdminListPermissionsQuery lastQuery;

    @Override
    public PageResult<Permission> findAll(AdminListPermissionsQuery query) {
      lastQuery = query;
      return new PageResult<>(
        List.of(permission()),
        query.page().page(),
        query.page().size(),
        1,
        1,
        true,
        true,
        query.sort()
      );
    }
  }

  private static Role role() {
    return new Role(
      1L,
      "ADMIN",
      "Administrator",
      "Default administrator role",
      true,
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      "system",
      "system"
    );
  }

  private static Permission permission() {
    return new Permission(
      10L,
      "admin:users:read",
      "Read admin users",
      "Allows reading user records",
      "admin-users",
      "read",
      true,
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      OffsetDateTime.parse("2026-05-01T06:30:00Z"),
      "system",
      "system"
    );
  }
}
