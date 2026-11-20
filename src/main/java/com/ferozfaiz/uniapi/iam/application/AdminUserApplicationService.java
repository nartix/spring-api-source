package com.ferozfaiz.uniapi.iam.application;

import com.ferozfaiz.uniapi.iam.application.command.AdminCreateUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserMutationCommand;
import com.ferozfaiz.uniapi.iam.application.command.AdminUserStatusCommand;
import com.ferozfaiz.uniapi.iam.application.command.RegisterUserCommand;
import com.ferozfaiz.uniapi.iam.application.command.UpdateUserCommand;
import com.ferozfaiz.uniapi.iam.application.port.AdminUserListingRepository;
import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.model.UserPermission;
import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.domain.repository.PermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.RoleRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserPermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRoleRepository;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.text.Text;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminUserApplicationService {

  private static final String SYSTEM_ACTOR = "system";

  private final UserApplicationService users;
  private final AdminUserListingRepository listings;
  private final RoleRepository roles;
  private final UserRoleRepository userRoles;
  private final PermissionRepository permissions;
  private final UserPermissionRepository userPermissions;
  private final Clock clock;

  @Autowired
  public AdminUserApplicationService(
    UserApplicationService users,
    AdminUserListingRepository listings,
    RoleRepository roles,
    UserRoleRepository userRoles,
    PermissionRepository permissions,
    UserPermissionRepository userPermissions
  ) {
    this(users, listings, roles, userRoles, permissions, userPermissions, Clock.systemUTC());
  }

  AdminUserApplicationService(
    UserApplicationService users,
    AdminUserListingRepository listings,
    RoleRepository roles,
    UserRoleRepository userRoles,
    PermissionRepository permissions,
    UserPermissionRepository userPermissions,
    Clock clock
  ) {
    this.users = users;
    this.listings = listings;
    this.roles = roles;
    this.userRoles = userRoles;
    this.permissions = permissions;
    this.userPermissions = userPermissions;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResult<AdminUserListItem> findAll(AdminListUsersQuery query) {
    return listings.findAll(query);
  }

  @Transactional(readOnly = true)
  public User findById(Long id) {
    return users.findById(id);
  }

  public User create(AdminCreateUserCommand command) {
    User created = users.register(
      new RegisterUserCommand(
        command.username(),
        command.email(),
        command.rawPassword(),
        command.firstName(),
        command.lastName()
      )
    );

    User saved = command.active()
      ? created
      : users.update(
          created.id(),
          new UpdateUserCommand(
            command.username(),
            command.email(),
            command.firstName(),
            command.lastName(),
            false
          )
        );
    syncAssignments(saved.id(), command.roles(), command.permissions());
    return saved;
  }

  public User update(Long id, AdminUserMutationCommand command) {
    User updated = users.update(
      id,
      new UpdateUserCommand(
        command.username(),
        command.email(),
        command.firstName(),
        command.lastName(),
        command.active()
      )
    );
    syncAssignments(updated.id(), command.roles(), command.permissions());
    return updated;
  }

  public User updateStatus(Long id, AdminUserStatusCommand command) {
    return users.updateStatus(id, command);
  }

  public void delete(Long id) {
    users.delete(id);
  }

  private void syncAssignments(Long userId, List<String> roleCodes, List<String> permissionCodes) {
    if (roleCodes != null) {
      syncRoles(userId, roleCodes);
    }
    if (permissionCodes != null) {
      syncPermissions(userId, permissionCodes);
    }
  }

  private void syncRoles(Long userId, List<String> requestedRoleCodes) {
    Map<String, Role> rolesByCode = roles
      .findAll()
      .stream()
      .filter(Role::active)
      .collect(Collectors.toMap(Role::code, Function.identity(), (left, right) -> left));
    Set<Long> requestedRoleIds = requestedRoleCodes(requestedRoleCodes)
      .stream()
      .map(roleCode -> roleByCode(rolesByCode, roleCode).id())
      .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<Long> currentRoleIds = userRoles
      .findByUserId(userId)
      .stream()
      .map(UserRole::roleId)
      .collect(Collectors.toCollection(LinkedHashSet::new));

    for (Long roleId : currentRoleIds) {
      if (!requestedRoleIds.contains(roleId)) {
        userRoles.deleteByUserIdAndRoleId(userId, roleId);
      }
    }
    for (Long roleId : requestedRoleIds) {
      if (!currentRoleIds.contains(roleId)) {
        userRoles.save(
          new UserRole(null, userId, roleId, OffsetDateTime.now(clock), null, SYSTEM_ACTOR)
        );
      }
    }
  }

  private void syncPermissions(Long userId, List<String> requestedPermissionCodes) {
    Map<String, Permission> permissionsByCode = permissions
      .findAll()
      .stream()
      .filter(Permission::active)
      .collect(Collectors.toMap(Permission::code, Function.identity(), (left, right) -> left));
    Set<Long> requestedPermissionIds = requestedPermissionCodes(requestedPermissionCodes)
      .stream()
      .map(permissionCode -> permissionByCode(permissionsByCode, permissionCode).id())
      .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<Long> currentPermissionIds = userPermissions
      .findByUserId(userId)
      .stream()
      .map(UserPermission::permissionId)
      .collect(Collectors.toCollection(LinkedHashSet::new));

    for (Long permissionId : currentPermissionIds) {
      if (!requestedPermissionIds.contains(permissionId)) {
        userPermissions.deleteByUserIdAndPermissionId(userId, permissionId);
      }
    }
    for (Long permissionId : requestedPermissionIds) {
      if (!currentPermissionIds.contains(permissionId)) {
        userPermissions.save(
          new UserPermission(
            null,
            userId,
            permissionId,
            OffsetDateTime.now(clock),
            null,
            SYSTEM_ACTOR
          )
        );
      }
    }
  }

  private static Set<String> requestedRoleCodes(List<String> requestedRoleCodes) {
    return requestedCodes(requestedRoleCodes, "iam.api.admin-user.role.unknown", "role");
  }

  private static Set<String> requestedPermissionCodes(List<String> requestedPermissionCodes) {
    return requestedCodes(
      requestedPermissionCodes,
      "iam.api.admin-user.permission.unknown",
      "permission"
    );
  }

  private static Set<String> requestedCodes(
    List<String> requestedCodes,
    String messageKey,
    String label
  ) {
    Set<String> codes = new LinkedHashSet<>();
    for (String requestedCode : requestedCodes) {
      String code = Text.trimToNull(requestedCode);
      if (code == null) {
        throw unknownCode(messageKey, label, requestedCode);
      }
      codes.add(code);
    }
    return codes;
  }

  private static Role roleByCode(Map<String, Role> rolesByCode, String code) {
    Role role = rolesByCode.get(code);
    if (role == null) {
      throw unknownCode("iam.api.admin-user.role.unknown", "role", code);
    }
    return role;
  }

  private static Permission permissionByCode(
    Map<String, Permission> permissionsByCode,
    String code
  ) {
    Permission permission = permissionsByCode.get(code);
    if (permission == null) {
      throw unknownCode("iam.api.admin-user.permission.unknown", "permission", code);
    }
    return permission;
  }

  private static InvalidRequestException unknownCode(String messageKey, String label, String code) {
    return new InvalidRequestException(
      messageKey,
      label + " must exist: " + String.valueOf(code),
      String.valueOf(code)
    );
  }
}
