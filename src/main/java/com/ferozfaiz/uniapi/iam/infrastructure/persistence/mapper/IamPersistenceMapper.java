package com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper;

import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.domain.model.Role;
import com.ferozfaiz.uniapi.iam.domain.model.RolePermission;
import com.ferozfaiz.uniapi.iam.domain.model.User;
import com.ferozfaiz.uniapi.iam.domain.model.UserPermission;
import com.ferozfaiz.uniapi.iam.domain.model.UserRole;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.PermissionEntity;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.RoleEntity;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.RolePermissionEntity;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.UserEntity;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.UserPermissionEntity;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.UserRoleEntity;

public final class IamPersistenceMapper {

  private IamPersistenceMapper() {}

  public static User toDomain(UserEntity entity) {
    return new User(
      entity.getId(),
      entity.getUsername(),
      entity.getEmail(),
      entity.getPasswordHash(),
      entity.getFirstName(),
      entity.getLastName(),
      entity.isActive(),
      entity.isAccountNonExpired(),
      entity.isAccountNonLocked(),
      entity.isCredentialsNonExpired(),
      entity.getLastLoginAt(),
      entity.getLastActivityAt(),
      entity.getCreatedAt(),
      entity.getUpdatedAt(),
      entity.getCreatedBy(),
      entity.getUpdatedBy()
    );
  }

  public static UserEntity toEntity(User user) {
    return UserEntity.builder()
      .id(user.id())
      .username(user.username())
      .email(user.email())
      .passwordHash(user.passwordHash())
      .firstName(user.firstName())
      .lastName(user.lastName())
      .active(user.active())
      .accountNonExpired(user.accountNonExpired())
      .accountNonLocked(user.accountNonLocked())
      .credentialsNonExpired(user.credentialsNonExpired())
      .lastLoginAt(user.lastLoginAt())
      .lastActivityAt(user.lastActivityAt())
      .createdAt(user.createdAt())
      .updatedAt(user.updatedAt())
      .createdBy(user.createdBy())
      .updatedBy(user.updatedBy())
      .build();
  }

  public static Role toDomain(RoleEntity entity) {
    return new Role(
      entity.getId(),
      entity.getCode(),
      entity.getName(),
      entity.getDescription(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt(),
      entity.getCreatedBy(),
      entity.getUpdatedBy()
    );
  }

  public static RoleEntity toEntity(Role role) {
    return RoleEntity.builder()
      .id(role.id())
      .code(role.code())
      .name(role.name())
      .description(role.description())
      .active(role.active())
      .createdAt(role.createdAt())
      .updatedAt(role.updatedAt())
      .createdBy(role.createdBy())
      .updatedBy(role.updatedBy())
      .build();
  }

  public static Permission toDomain(PermissionEntity entity) {
    return new Permission(
      entity.getId(),
      entity.getCode(),
      entity.getName(),
      entity.getDescription(),
      entity.getResource(),
      entity.getAction(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt(),
      entity.getCreatedBy(),
      entity.getUpdatedBy()
    );
  }

  public static PermissionEntity toEntity(Permission permission) {
    return PermissionEntity.builder()
      .id(permission.id())
      .code(permission.code())
      .name(permission.name())
      .description(permission.description())
      .resource(permission.resource())
      .action(permission.action())
      .active(permission.active())
      .createdAt(permission.createdAt())
      .updatedAt(permission.updatedAt())
      .createdBy(permission.createdBy())
      .updatedBy(permission.updatedBy())
      .build();
  }

  public static UserRole toDomain(UserRoleEntity entity) {
    return new UserRole(
      entity.getId(),
      entity.getUserId(),
      entity.getRoleId(),
      entity.getAssignedAt(),
      entity.getExpiresAt(),
      entity.getCreatedBy()
    );
  }

  public static UserRoleEntity toEntity(UserRole userRole) {
    return UserRoleEntity.builder()
      .id(userRole.id())
      .userId(userRole.userId())
      .roleId(userRole.roleId())
      .assignedAt(userRole.assignedAt())
      .expiresAt(userRole.expiresAt())
      .createdBy(userRole.createdBy())
      .build();
  }

  public static UserPermission toDomain(UserPermissionEntity entity) {
    return new UserPermission(
      entity.getId(),
      entity.getUserId(),
      entity.getPermissionId(),
      entity.getGrantedAt(),
      entity.getExpiresAt(),
      entity.getCreatedBy()
    );
  }

  public static UserPermissionEntity toEntity(UserPermission userPermission) {
    return UserPermissionEntity.builder()
      .id(userPermission.id())
      .userId(userPermission.userId())
      .permissionId(userPermission.permissionId())
      .grantedAt(userPermission.grantedAt())
      .expiresAt(userPermission.expiresAt())
      .createdBy(userPermission.createdBy())
      .build();
  }

  public static RolePermission toDomain(RolePermissionEntity entity) {
    return new RolePermission(
      entity.getId(),
      entity.getRoleId(),
      entity.getPermissionId(),
      entity.getGrantedAt(),
      entity.getCreatedBy()
    );
  }

  public static RolePermissionEntity toEntity(RolePermission rolePermission) {
    return RolePermissionEntity.builder()
      .id(rolePermission.id())
      .roleId(rolePermission.roleId())
      .permissionId(rolePermission.permissionId())
      .grantedAt(rolePermission.grantedAt())
      .createdBy(rolePermission.createdBy())
      .build();
  }
}
