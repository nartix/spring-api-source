package com.ferozfaiz.uniapi.iam.application.auth;

import com.ferozfaiz.uniapi.iam.api.auth.AuthenticatedAuthorities;
import com.ferozfaiz.uniapi.iam.api.auth.IdentityAuthorityLookup;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.model.UserPermission;
import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.domain.repository.PermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.RolePermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.RoleRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserPermissionRepository;
import com.ferozfaiz.uniapi.iam.domain.repository.UserRoleRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class IamIdentityAuthorityLookup implements IdentityAuthorityLookup {

  private final UserRoleRepository userRoles;
  private final RoleRepository roles;
  private final RolePermissionRepository rolePermissions;
  private final UserPermissionRepository userPermissions;
  private final PermissionRepository permissions;
  private final Clock clock;

  @Autowired
  IamIdentityAuthorityLookup(
    UserRoleRepository userRoles,
    RoleRepository roles,
    RolePermissionRepository rolePermissions,
    UserPermissionRepository userPermissions,
    PermissionRepository permissions
  ) {
    this(userRoles, roles, rolePermissions, userPermissions, permissions, Clock.systemUTC());
  }

  IamIdentityAuthorityLookup(
    UserRoleRepository userRoles,
    RoleRepository roles,
    RolePermissionRepository rolePermissions,
    UserPermissionRepository userPermissions,
    PermissionRepository permissions,
    Clock clock
  ) {
    this.userRoles = userRoles;
    this.roles = roles;
    this.rolePermissions = rolePermissions;
    this.userPermissions = userPermissions;
    this.permissions = permissions;
    this.clock = clock;
  }

  @Override
  public AuthenticatedAuthorities findAuthorities(Long userId) {
    if (userId == null) {
      return AuthenticatedAuthorities.empty();
    }

    OffsetDateTime now = OffsetDateTime.now(clock);
    Set<Long> activeRoleIds = new LinkedHashSet<>();
    Set<String> roleCodes = new LinkedHashSet<>();
    Set<Long> permissionIds = new LinkedHashSet<>();

    for (UserRole userRole : userRoles.findByUserId(userId)) {
      if (isExpired(userRole.expiresAt(), now)) {
        continue;
      }
      roles
        .findById(userRole.roleId())
        .filter(Role::active)
        .ifPresent(role -> {
          activeRoleIds.add(role.id());
          roleCodes.add(role.code());
        });
    }

    for (Long roleId : activeRoleIds) {
      rolePermissions
        .findByRoleId(roleId)
        .forEach(rolePermission -> permissionIds.add(rolePermission.permissionId()));
    }

    for (UserPermission userPermission : userPermissions.findByUserId(userId)) {
      if (!isExpired(userPermission.expiresAt(), now)) {
        permissionIds.add(userPermission.permissionId());
      }
    }

    Set<String> permissionCodes = new LinkedHashSet<>();
    for (Long permissionId : permissionIds) {
      permissions
        .findById(permissionId)
        .filter(Permission::active)
        .ifPresent(permission -> permissionCodes.add(permission.code()));
    }

    return new AuthenticatedAuthorities(Set.copyOf(roleCodes), Set.copyOf(permissionCodes));
  }

  private static boolean isExpired(OffsetDateTime expiresAt, OffsetDateTime now) {
    return expiresAt != null && !expiresAt.isAfter(now);
  }
}
