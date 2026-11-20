package com.ferozfaiz.uniapi.iam.domain.repository;

import com.ferozfaiz.uniapi.iam.domain.model.RolePermission;
import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository {
  RolePermission save(RolePermission rolePermission);

  Optional<RolePermission> findById(Long id);

  List<RolePermission> findByRoleId(Long roleId);

  List<RolePermission> findByPermissionId(Long permissionId);

  boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

  void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
